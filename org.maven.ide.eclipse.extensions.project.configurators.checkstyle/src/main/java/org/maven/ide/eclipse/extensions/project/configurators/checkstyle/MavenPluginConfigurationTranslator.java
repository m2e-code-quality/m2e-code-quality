package org.maven.ide.eclipse.extensions.project.configurators.checkstyle;

import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.LOG_PREFIX;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.projectconfig.FileMatchPattern;
import net.sf.eclipsecs.core.projectconfig.FileSet;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationWorkingCopy;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

import org.apache.http.client.utils.URIUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;
import org.maven.ide.eclipse.extensions.shared.util.ResourceResolver;

/**
 * Utility class to get checkstyle plugin configuration.
 */
public class MavenPluginConfigurationTranslator {
    private static final String CHECKSTYLE_DEFAULT_CONFIG_LOCATION = "config/sun_checks.xml";
    /** checkstyle maven plugin artifactId */
    private static final Map<String, String> PATTERNS_CACHE =
        new HashMap<String, String>();

    private final MavenConsole console;
    private final MavenProject mavenProject;
    private final IProject project;
    private final URI basedirUri;
    private final MavenPluginConfigurationExtractor cfgExtractor;
    private final String prefix;
    private final ResourceResolver resourceResolver;
    
    private MavenPluginConfigurationTranslator(
        final MavenProject mavenProject,
        final MavenPluginWrapper pluginWrapper,
        final IProject project,
        final String prefix) {
        this.console = MavenPlugin.getDefault().getConsole();
        this.mavenProject = mavenProject;
        this.project = project;
        this.basedirUri = this.project.getLocationURI();
        this.cfgExtractor = MavenPluginConfigurationExtractor.newInstance(pluginWrapper);
        this.prefix = prefix;
        this.resourceResolver = ResourceResolver.newInstance(pluginWrapper, prefix);
    }

    public URL getRuleset() 
        throws CheckstylePluginException {
        final URL ruleset = this.resourceResolver.resolveLocation(
                this.getConfigLocation());
        if (ruleset == null) {
            throw new CheckstylePluginException(String.format(
               "Failed to resolve RuleSet from configLocation,SKIPPING Eclipse checkstyle configuration"));
        }
        return ruleset;
    }

    public void updateCheckConfigWithIncludeExcludePatterns(
            final ProjectConfigurationWorkingCopy pcWorkingCopy, final ICheckConfiguration checkCfg) 
        throws CheckstylePluginException {
        final FileSet fs = new FileSet("java-sources", checkCfg);
        fs.setEnabled(true);
        //add fileset includes/excludes
        fs.setFileMatchPatterns(this.getIncludesExcludesFileMatchPatterns());
        //now add the config
        pcWorkingCopy.getFileSets().clear();
        pcWorkingCopy.getFileSets().add(fs);
    }

    public Properties getConfiguredProperties() throws CheckstylePluginException {
        final Properties properties = new Properties();
        final String propertiesLocation = this.getPropertiesLocation();
        if (propertiesLocation != null) {
            final URL url = this.resourceResolver.resolveLocation(propertiesLocation);
            if (url == null) {
                throw new CheckstylePluginException(String.format(
                        "Failed to resolve propertiesLocation [%s]",
                        propertiesLocation));
            }
            try {
                properties.load(url.openStream());
            } catch (IOException e) {
                throw new CheckstylePluginException(String.format(
                        "Failed to LOAD properties from propertiesLocation [%s]",
                        propertiesLocation));
            }
        }
        return properties;
    }

    public void updatePropertiesWithPropertyExpansion(final Properties props) 
        throws CheckstylePluginException {
        final String propertyExpansion = this.getPropertyExpansion();
        if (propertyExpansion != null) {
            try {
                props.load(new ByteArrayInputStream(propertyExpansion.getBytes()));
            } catch (IOException e) {
                throw new CheckstylePluginException(String.format(
                        "[%s]: Failed to checkstyle propertyExpansion [%s]",
                        LOG_PREFIX, propertyExpansion));
            }
        }
    }
    
    /**
     * Get the {@literal propertiesLocation} element if present in the configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     */
    private String getPropertiesLocation() {
        return this.cfgExtractor.value(null, "propertiesLocation");
    }

    /**
     * Get the {@literal propertyExpansion} element if present in the configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     */
    private String getPropertyExpansion() {
        return this.cfgExtractor.value(null, "propertyExpansion");
    }

    /**
     * Get the {@literal includeTestSourceDirectory} element value if present in the configuration.
     * 
     * @return the value of the {@code includeTestSourceDirectory} element.
     */
    public boolean getIncludeTestSourceDirectory() {
        return this.cfgExtractor.asBoolean(null, "includeTestSourceDirectory");
    }
    
    /**
     * Get the {@literal configLocation} element if present in the configuration.
     * 
     * @return the value of the {@code configLocation} element.
     */
    private String getConfigLocation() {
        String configLocation = this.cfgExtractor.value(null, "configLocation");
        if (configLocation == null) {
            configLocation = CHECKSTYLE_DEFAULT_CONFIG_LOCATION;
        }
        return configLocation;
    }
    
    private List<String> getIncludes() {
        return this.getPatterns("includes");
    }
    
    private List<String> getExcludes() {
        return this.getPatterns("excludes");
    }
    
    /**
     * 
     * @return                           A list of {@code FileMatchPattern}'s.
     * @throws CheckstylePluginException if an error occurs getting the include
     *                                   exclude patterns.
     */
    private List<FileMatchPattern> getIncludesExcludesFileMatchPatterns()
        throws CheckstylePluginException {

        final List<FileMatchPattern> patterns = new LinkedList<FileMatchPattern>();
        
        /**
         * Step 1). Get all the source roots (including test sources root, if enabled).
         */
        Set<String> sourceFolders = new HashSet<String>(
                this.mavenProject.getCompileSourceRoots());
        if (this.getIncludeTestSourceDirectory()) {
            sourceFolders.addAll(this.mavenProject.getTestCompileSourceRoots());
        }
        
        /**
         * Step 2). Get all the includes patterns add them for all source roots.
         * NOTES:
         *  - Since the eclipse-cs excludes override (or more correctly the later ones)
         *    the includes, we make sure that if no include patterns are specified we
         *    at least put the source folders.
         *  - Also, we use relative path to project root.
         */
        final List<String> includePatterns = this.getIncludes();
        for (String folder : sourceFolders) {
            final String folderRelativePath = this.basedirUri
                .relativize(new File(folder).toURI())
                .getPath();
            if (includePatterns.size() != 0) {
                patterns.addAll(this.normalizePatternsToCheckstyleFileMathcPattern(
                    includePatterns, 
                    this.convertToEclipseCheckstyleRegExpPath(folderRelativePath), 
                    true));
            } else {
                patterns.add(new FileMatchPattern(folderRelativePath));
            }
        }
        
        /**
         * Step 3). Get all the excludes patterns add them for all source roots.
         * NOTES:
         *  - Since the eclipse-cs excludes override (or more correctly the later ones)
         *    the includes, we do NOT add the sourceFolder to exclude list.
         */
        final List<String> excludePatterns = this.getExcludes();
        for (String folder : sourceFolders) {
            String folderRelativePath = URIUtils
                .resolve(this.basedirUri, new File(folder).toURI())
                .getPath();
            patterns.addAll(this.normalizePatternsToCheckstyleFileMathcPattern(
                    excludePatterns, 
                    this.convertToEclipseCheckstyleRegExpPath(folderRelativePath), 
                    false));
        }
        
        return patterns;
    }
    
    private String convertToEclipseCheckstyleRegExpPath(final String path) {
        String csCompatiblePath = path;
        if (path.endsWith("/")) {
            csCompatiblePath = path.substring(0, path.length() - 1);
        } 
        //we append the .* pattern regardless
        csCompatiblePath = csCompatiblePath + ".*";
        return csCompatiblePath;
    }
    
    private List<FileMatchPattern> normalizePatternsToCheckstyleFileMathcPattern(
            final List<String> patterns, final String relativePath, 
            final boolean setIsIncludePatternFlag) 
            throws CheckstylePluginException {
        List<FileMatchPattern> fileMatchPatterns = new LinkedList<FileMatchPattern>();
        for (String p: patterns) {
            final FileMatchPattern fmp = new FileMatchPattern(
                    String.format("%s%s", relativePath, p));
            fmp.setIsIncludePattern(setIsIncludePatternFlag);
            fileMatchPatterns.add(fmp);
        }
        return fileMatchPatterns;
    }
    
    /**
     * Get the include or exclude patterns from the plugin configuration. 
     * NOTE: this has to be unfortunately transformed, as Maven Plugin configuration supports 
     * ANT style pattern but the Eclipse-CS requires java regex.
     * 
     * @param elemName the parent element name (e.g. {@code <includes> or <excludes>}.
     * @return         a {@code List} of include patterns.
     */
    private List<String> getPatterns(String elemName) {
        List<String> transformedPatterns = new LinkedList<String>();
        final String patternsString = this.cfgExtractor.value(null, elemName);
        if (patternsString == null || patternsString.length() == 0) {
            return transformedPatterns;
        }
        final String[] patternsArray = StringUtils.split(patternsString, ",");
        for (String p : patternsArray) {
            p = StringUtils.strip(p);
            if (p == null || p.length() == 0) {
                continue;
            }
            String csPattern;
            if (PATTERNS_CACHE.containsKey(p)) {
                csPattern = PATTERNS_CACHE.get(p);
            } else {
                csPattern = this.convertAntStylePatternToCheckstylePattern(p);
                PATTERNS_CACHE.put(p, csPattern);
                this.console.logMessage(String.format(
                        "[%s]: Transformed plugin pattern [%s] to [%s]", 
                            this.prefix, p, csPattern));
            }
            transformedPatterns.add(csPattern);
        }
        return transformedPatterns;
    }
    
    /**
     * Helper to convert the maven-checkstyle-plugin includes/excludes pattern
     * to eclipse checkstyle plugin pattern.
     * 
     * @param pattern the maven-checkstyle-plugin pattern.
     * @return        the converted checkstyle eclipse pattern.
     */
    private String convertAntStylePatternToCheckstylePattern(final String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern cannot be null");
        }
        if (pattern.length() == 0) {
            throw new IllegalArgumentException("pattern cannot empty");
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); ++i) {
            final int nextCharIndex = i + 1;
            //mark the end so we can append the last char of string
            char nextChar = '\0';
            if (nextCharIndex != pattern.length()) {
                nextChar = pattern.charAt(nextCharIndex);
            }
            final char curChar = pattern.charAt(i);
            if (curChar == '*' && nextChar == '*') {
                sb.append(".*");
                ++i;
            } else if (curChar == '*') {
                sb.append(".*");
            } else if (curChar == '.') {
                sb.append("\\.");
            } else {
                sb.append(curChar);
            }
        }
        return sb.toString();
    }
    
    public static MavenPluginConfigurationTranslator newInstance(
            final MavenProject mavenProject,
            final MavenPluginWrapper mavenPlugin,
            final IProject project,
            final String prefix) {
        final MavenPluginConfigurationTranslator m2csConverter =
            new MavenPluginConfigurationTranslator(
                    mavenProject,
                    mavenPlugin,
                    project, 
                    prefix);
        return m2csConverter;
    }
}
