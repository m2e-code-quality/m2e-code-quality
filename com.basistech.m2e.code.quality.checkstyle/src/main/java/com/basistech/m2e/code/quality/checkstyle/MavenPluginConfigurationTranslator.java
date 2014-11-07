/*******************************************************************************
 * Copyright 2010 Mohan KR
 * Copyright 2010 Basis Technology Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.basistech.m2e.code.quality.checkstyle;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
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

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;

/**
 * Utility class to get checkstyle plugin configuration.
 */
public class MavenPluginConfigurationTranslator {

    private static final String CHECKSTYLE_DEFAULT_CONFIG_LOCATION =
            "config/sun_checks.xml";
    private static final String CHECKSTYLE_DEFAULT_SUPPRESSIONS_FILE_EXPRESSION =
            "checkstyle.suppressions.file";
    /** checkstyle maven plugin artifactId */
    private static final Map<String, String> PATTERNS_CACHE =
            new HashMap<String, String>();

    private final MavenProject mavenProject;
    private final IProject project;
    private final URI basedirUri;
    private final AbstractMavenPluginProjectConfigurator configurator;
    private final ResourceResolver resourceResolver;
    private final MavenSession session;
    private final MojoExecution execution;

    private MavenPluginConfigurationTranslator(
            final AbstractMavenPluginProjectConfigurator configurator,
            final MavenSession session, final MavenProject mavenProject,
            final MojoExecution mojoExecution, final IProject project)
            throws CoreException {
        this.mavenProject = mavenProject;
        this.project = project;
        this.basedirUri = this.project.getLocationURI();
        this.resourceResolver =
                ResourceResolver.newInstance(configurator.getPluginClassRealm(
                        session, mojoExecution));
        this.session = session;
        this.execution = mojoExecution;
        this.configurator = configurator;
    }

    public boolean isActive() throws CoreException {
        Boolean isSkip =
                configurator.getParameterValue("skip", Boolean.class, session,
                        execution);
        return (isSkip != null) ? !isSkip : true;
    }

    public URL getRuleset() throws CheckstylePluginException, CoreException {
        final URL ruleset =
                this.resourceResolver.resolveLocation(this.getConfigLocation());
        if (ruleset == null) {
            throw new CheckstylePluginException(
                    String.format("Failed to resolve RuleSet from configLocation,SKIPPING Eclipse checkstyle configuration"));
        }
        return ruleset;
    }

    public String getHeaderFile() throws CheckstylePluginException,
            CoreException {
        final URL headerResource =
                this.resourceResolver.resolveLocation(getHeaderLocation());
        if (headerResource == null) {
            return null;
        }

        String outDir = mavenProject.getBuild().getDirectory();
        File headerFile = new File(outDir, "checkstyle-header-" + getExecutionId() + ".txt");
        copyOut(headerResource, headerFile);

        return headerFile.getAbsolutePath();
    }

    public String getSuppressionsFile() throws CheckstylePluginException,
            CoreException {
        final String suppressionsLocation = getSuppressionsLocation();
        if (suppressionsLocation == null) {
            return null;
        }
        final URL suppressionsResource =
                this.resourceResolver.resolveLocation(suppressionsLocation);
        if (suppressionsResource == null) {
            return null;
        }

        String outDir = mavenProject.getBuild().getDirectory();
        File suppressionsFile = new File(outDir, "checkstyle-suppressions-" + getExecutionId() + ".xml");
        copyOut(suppressionsResource, suppressionsFile);

        return suppressionsFile.getAbsolutePath();
    }

    public String getSuppressionsFileExpression()
            throws CheckstylePluginException, CoreException {
        String suppressionsFileExpression =
                configurator.getParameterValue("suppressionsFileExpression",
                        String.class, session, execution);
        if (suppressionsFileExpression == null) {
            suppressionsFileExpression =
                    CHECKSTYLE_DEFAULT_SUPPRESSIONS_FILE_EXPRESSION;
        }
        return suppressionsFileExpression;
    }

    public void updateCheckConfigWithIncludeExcludePatterns(
            final ProjectConfigurationWorkingCopy pcWorkingCopy, final ICheckConfiguration checkCfg)
            throws CheckstylePluginException, CoreException {
		final FileSet fs = new FileSet("java-sources-" + getExecutionId(), checkCfg);
        fs.setEnabled(true);
        // add fileset includes/excludes
        fs.setFileMatchPatterns(this.getIncludesExcludesFileMatchPatterns());
        // now add the config
        pcWorkingCopy.getFileSets().add(fs);
    }

    /**
     * Get the {@literal propertiesLocation} element if present in the
     * configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     * @throws CoreException
     */
    protected String getPropertiesLocation() throws CoreException {
        return configurator.getParameterValue("propertiesLocation",
                String.class, session, execution);
    }

    /**
     * Get the {@literal propertyExpansion} element if present in the
     * configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     * @throws CoreException
     */
    protected String getPropertyExpansion() throws CoreException {
        return configurator.getParameterValue("propertyExpansion",
                String.class, session, execution);
    }

    /**
     * Get the {@literal includeTestSourceDirectory} element value if present in
     * the configuration.
     * 
     * @return the value of the {@code includeTestSourceDirectory} element.
     * @throws CoreException
     */
    public boolean getIncludeTestSourceDirectory() throws CoreException {
        Boolean includeTestSourceDirectory =
                configurator.getParameterValue("includeTestSourceDirectory",
                        Boolean.class, session, execution);
        if (includeTestSourceDirectory != null) {
            return includeTestSourceDirectory.booleanValue();
        } else {
            return false;
        }
    }

    public boolean getIncludeResourcesDirectory() throws CoreException {
        Boolean includeTestSourceDirectory =
                configurator.getParameterValue("includeResources",
                        Boolean.class, session, execution);
        if (includeTestSourceDirectory != null) {
            return includeTestSourceDirectory.booleanValue();
        } else {
            return false;
        }
    }

    public boolean getIncludeTestResourcesDirectory() throws CoreException {
        Boolean includeTestSourceDirectory =
                configurator.getParameterValue("includeTestResources",
                        Boolean.class, session, execution);
        if (includeTestSourceDirectory != null) {
            return includeTestSourceDirectory.booleanValue();
        } else {
            return false;
        }
    }
    
    public String getExecutionId() {
    	return execution.getExecutionId();
    }

    /**
     * Get the {@literal configLocation} element if present in the
     * configuration.
     * 
     * @return the value of the {@code configLocation} element.
     * @throws CoreException
     */
    private String getConfigLocation() throws CoreException {
        String configLocation =
                configurator.getParameterValue("configLocation", String.class,
                        session, execution);
        if (configLocation == null) {
            configLocation = CHECKSTYLE_DEFAULT_CONFIG_LOCATION;
        }
        return configLocation;
    }

    private String getHeaderLocation() throws CoreException {
        String configLocation = getConfigLocation();
        String headerLocation =
                configurator.getParameterValue("headerLocation", String.class,
                        session, execution);
        if ("config/maven_checks.xml".equals(configLocation)
                && "LICENSE.txt".equals(headerLocation)) {
            headerLocation = "config/maven-header.txt";
        }
        return headerLocation;
    }

    private String getSuppressionsLocation() throws CoreException {
        String suppressionsLocation =
                configurator.getParameterValue("suppressionsLocation",
                        String.class, session, execution);
        if (suppressionsLocation == null) {
            suppressionsLocation =
                    configurator.getParameterValue("suppressionsFile",
                            String.class, session, execution);
        }
        return suppressionsLocation;
    }
    
    private String getSourceDirectory() throws CoreException {
		return configurator.getParameterValue("sourceDirectory", String.class,
				session, execution);
    }

    // since maven-checkstyle-plugin 2.13
    private List<String> getSourceDirectories() throws CoreException {
        return configurator.getParameterValue("sourceDirectories", List.class, session, execution);
    }

    private String getTestSourceDirectory() throws CoreException {
    	return configurator.getParameterValue("testSourceDirectory", String.class,
    			session, execution);
    }

    // since maven-checkstyle-plugin 2.13
    private List<String> getTestSourceDirectories() throws CoreException {
        return configurator.getParameterValue("testSourceDirectories", List.class, session, execution);
    }

    private List<String> getIncludes() throws CoreException {
        return this.getPatterns("includes");
    }

    private List<String> getExcludes() throws CoreException {
        return this.getPatterns("excludes");
    }

    private List<String> getResourceIncludes() throws CoreException {
        return this.getPatterns("resourceIncludes");
    }

    private List<String> getResourceExcludes() throws CoreException {
        return this.getPatterns("resourceExcludes");
    }

    private void copyOut(URL src, File dest) throws CheckstylePluginException {
        try {
            FileUtils.copyURLToFile(src, dest);
        } catch (IOException e) {
            throw new CheckstylePluginException("Failed to copy file "
                    + src.getFile()
                    + ", SKIPPING Eclipse checkstyle configuration");
        }
    }

    /**
     * 
     * @return A list of {@code FileMatchPattern}'s.
     * @throws CheckstylePluginException
     *             if an error occurs getting the include exclude patterns.
     * @throws CoreException
     */
    private List<FileMatchPattern> getIncludesExcludesFileMatchPatterns()
            throws CheckstylePluginException, CoreException {

        final List<FileMatchPattern> patterns =
                new LinkedList<FileMatchPattern>();

        /**
         * Step 1). Get all the source roots (including test sources root, if
         * enabled).
         */
        Set<String> sourceFolders = new HashSet<String>();
        String sourceDirectory = this.getSourceDirectory();
        if (sourceDirectory != null) {
            sourceFolders.add(sourceDirectory);
        }
        List<String> sourceDirectories = this.getSourceDirectories();
        if (sourceDirectories != null) {
            sourceFolders.addAll(sourceDirectories);
        }

        if (getIncludeTestSourceDirectory()) {
            String testSourceDirectory = this.getTestSourceDirectory();
            if (testSourceDirectory != null) {
                sourceFolders.add(testSourceDirectory);
            }
            List<String> testSourceDirectories = this.getTestSourceDirectories();
            if (testSourceDirectories != null) {
                sourceFolders.addAll(testSourceDirectories);
            }
        }

        /**
         * Step 2). Get all the includes patterns add them for all source roots.
         * NOTES: - Since the eclipse-cs excludes override (or more correctly
         * the later ones) the includes, we make sure that if no include
         * patterns are specified we at least put the source folders. - Also, we
         * use relative path to project root.
         */
        final List<String> includePatterns = this.getIncludes();
        for (String folder : sourceFolders) {
            final String folderRelativePath =
                    this.basedirUri.relativize(new File(folder).toURI())
                            .getPath();
            if (includePatterns.size() != 0) {
                patterns.addAll(this
                        .normalizePatternsToCheckstyleFileMatchPattern(
                                includePatterns, folderRelativePath, true));
            } else {
                patterns.add(new FileMatchPattern(folderRelativePath));
            }
        }

        /**
         * Step 3). Get all the excludes patterns add them for all source roots.
         * NOTES: - Since the eclipse-cs excludes override (or more correctly
         * the later ones) the includes, we do NOT add the sourceFolder to
         * exclude list.
         */
        final List<String> excludePatterns = this.getExcludes();
        for (String folder : sourceFolders) {
            String folderRelativePath =
                    this.basedirUri.relativize(new File(folder).toURI())
                            .getPath();
            patterns.addAll(this.normalizePatternsToCheckstyleFileMatchPattern(
                    excludePatterns,
                    this.convertToEclipseCheckstyleRegExpPath(folderRelativePath),
                    false));
        }

        if (this.getIncludeResourcesDirectory()) {
            final List<String> resourceIncludePatterns =
                    this.getResourceIncludes();
            for (Resource resource : this.mavenProject.getBuild()
                    .getResources()) {
                String folderRelativePath =
                        this.basedirUri.relativize(
                                new File(resource.getDirectory()).toURI())
                                .getPath();
                patterns.addAll(this
                        .normalizePatternsToCheckstyleFileMatchPattern(
                                resourceIncludePatterns, folderRelativePath,
                                true));
            }

            final List<String> resourceExcludePatterns =
                    this.getResourceExcludes();
            for (Resource resource : this.mavenProject.getBuild()
                    .getResources()) {
                String folderRelativePath =
                        this.basedirUri.relativize(
                                new File(resource.getDirectory()).toURI())
                                .getPath();
                patterns.addAll(this
                        .normalizePatternsToCheckstyleFileMatchPattern(
                                resourceExcludePatterns, folderRelativePath,
                                false));
            }
        }

        if (this.getIncludeTestResourcesDirectory()) {
            final List<String> resourceIncludePatterns =
                    this.getResourceIncludes();
            for (Resource resource : this.mavenProject.getBuild()
                    .getTestResources()) {
                if (resource.getExcludes().size() > 0
                        || resource.getIncludes().size() > 0) {
                    // ignore resources that have ex/includes for now
                    continue;
                }
                String folderRelativePath =
                        this.basedirUri.relativize(
                                new File(resource.getDirectory()).toURI())
                                .getPath();
                patterns.addAll(this
                        .normalizePatternsToCheckstyleFileMatchPattern(
                                resourceIncludePatterns, folderRelativePath,
                                true));
            }

            final List<String> resourceExcludePatterns =
                    this.getResourceExcludes();
            for (Resource resource : this.mavenProject.getBuild()
                    .getTestResources()) {
                if (resource.getExcludes().size() > 0
                        || resource.getIncludes().size() > 0) {
                    // ignore resources that have ex/includes for now
                    continue;
                }
                String folderRelativePath =
                        this.basedirUri.relativize(
                                new File(resource.getDirectory()).toURI())
                                .getPath();
                patterns.addAll(this
                        .normalizePatternsToCheckstyleFileMatchPattern(
                                resourceExcludePatterns, folderRelativePath,
                                false));
            }
        }

        return patterns;
    }

    private String convertToEclipseCheckstyleRegExpPath(final String path) {
        String csCompatiblePath = path;
        if (path.endsWith("/")) {
            csCompatiblePath = path.substring(0, path.length() - 1);
        }
        // we append the .* pattern regardless
        csCompatiblePath = csCompatiblePath + ".*";
        return csCompatiblePath;
    }

    private List<FileMatchPattern> normalizePatternsToCheckstyleFileMatchPattern(
            final List<String> patterns, final String relativePath,
            final boolean setIsIncludePatternFlag)
            throws CheckstylePluginException {
        List<FileMatchPattern> fileMatchPatterns =
                new LinkedList<FileMatchPattern>();
        for (String p : patterns) {
            final FileMatchPattern fmp =
                    new FileMatchPattern(String.format("%s%s", relativePath, p));
            fmp.setIsIncludePattern(setIsIncludePatternFlag);
            fileMatchPatterns.add(fmp);
        }
        return fileMatchPatterns;
    }

    /**
     * Get the include or exclude patterns from the plugin configuration. NOTE:
     * this has to be unfortunately transformed, as Maven Plugin configuration
     * supports ANT style pattern but the Eclipse-CS requires java regex.
     * 
     * @param elemName
     *            the parent element name (e.g. {@code <includes> or <excludes>}
     *            .
     * @return a {@code List} of include patterns.
     * @throws CoreException
     */
    private List<String> getPatterns(String elemName) throws CoreException {
        List<String> transformedPatterns = new LinkedList<String>();
        final String patternsString =
                configurator.getParameterValue(elemName, String.class, session,
                        execution);
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
            }
            transformedPatterns.add(csPattern);
        }
        return transformedPatterns;
    }

    /**
     * Helper to convert the maven-checkstyle-plugin includes/excludes pattern
     * to eclipse checkstyle plugin pattern.
     * 
     * @param pattern
     *            the maven-checkstyle-plugin pattern.
     * @return the converted checkstyle eclipse pattern.
     */
    private String convertAntStylePatternToCheckstylePattern(String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern cannot be null");
        }
        if (pattern.length() == 0) {
            throw new IllegalArgumentException("pattern cannot empty");
        }

        pattern =
                pattern.replace(File.separatorChar == '/' ? '\\' : '/',
                        File.separatorChar);
        String dupeSeperatorChar = File.separator + File.separator;
        while (pattern.contains(dupeSeperatorChar)) {
            pattern = pattern.replace(dupeSeperatorChar, File.separator);
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); ++i) {
            final char curChar = pattern.charAt(i);
            char nextChar = '\0';
            char nextNextChar = '\0';
            if (i + 1 < pattern.length()) {
                nextChar = pattern.charAt(i + 1);
            }
            if (i + 2 < pattern.length()) {
                nextNextChar = pattern.charAt(i + 2);
            }

            if (curChar == '*' && nextChar == '*'
                    && nextNextChar == File.separatorChar) {
                sb.append(".*");
                ++i;
                ++i;
            } else if (curChar == '*') {
                sb.append(".*");
            } else if (curChar == '.') {
                sb.append("\\.");
            } else {
                sb.append(curChar);
            }
        }
        String result = sb.toString();
        if (result.endsWith(File.separator)) {
            result += ".*";
        }

        // cleanup the resulting regex pattern
        while (result.contains(".*.*")) {
            result = result.replace(".*.*", ".*");
        }

        return result;
    }

    public Properties getConfiguredProperties() throws CoreException,
            CheckstylePluginException {
        String propertiesLocation = getPropertiesLocation();
        Properties properties = new Properties();
        if (propertiesLocation != null) {
            final URL url =
                    this.resourceResolver.resolveLocation(propertiesLocation);
            if (url == null) {
                throw new CheckstylePluginException(String.format(
                        "Failed to resolve propertiesLocation [%s]",
                        propertiesLocation));
            }
            try {
                properties.load(url.openStream());
            } catch (IOException e) {
                throw new CheckstylePluginException(
                        String.format(
                                "Failed to LOAD properties from propertiesLocation [%s]",
                                propertiesLocation));
            }
        }
        return properties;
    }

    public void updatePropertiesWithPropertyExpansion(Properties props)
            throws CheckstylePluginException, CoreException {
        final String propertyExpansion = this.getPropertyExpansion();
        if (propertyExpansion != null) {
            try {
                // beware of windows path separator
                final String escapedPropertyExpansion =
                        StringEscapeUtils.escapeJava(propertyExpansion);
                props.load(new StringReader(escapedPropertyExpansion));
            } catch (IOException e) {
                throw new CheckstylePluginException(String.format(
                        "[%s]: Failed to checkstyle propertyExpansion [%s]",
                        CheckstyleEclipseConstants.LOG_PREFIX,
                        propertyExpansion));
            }
        }
    }

    public static List<MavenPluginConfigurationTranslator> newInstance(
            AbstractMavenPluginProjectConfigurator configurator,
            MavenSession session, final MavenProject mavenProject,
            final MavenPluginWrapper mavenPlugin, final IProject project)
            throws CoreException {
		final List<MavenPluginConfigurationTranslator> m2csConverters = new ArrayList<MavenPluginConfigurationTranslator>();
		for (final MojoExecution execution : mavenPlugin.getMojoExecutions()) {
			m2csConverters.add(new MavenPluginConfigurationTranslator(
					configurator, session, mavenProject, execution, project));
		}
		return m2csConverters;
    }

}
