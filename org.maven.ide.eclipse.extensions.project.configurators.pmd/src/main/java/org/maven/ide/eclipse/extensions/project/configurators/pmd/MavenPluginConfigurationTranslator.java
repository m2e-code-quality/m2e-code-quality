package org.maven.ide.eclipse.extensions.project.configurators.pmd;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.utils.URIUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;

import com.google.common.collect.ImmutableList;

/**
 * Utility class to get maven-pmd-plugin configuration.
 */
public class MavenPluginConfigurationTranslator {

    private static final Map<String, String> PATTERNS_CACHE =
        new HashMap<String, String>();
    
    private final MavenProject mavenProject;
    private final MavenConsole console;
    private final URI basedirUri;
    private final MavenPluginConfigurationExtractor cfgExtractor;
    private final List<String> excludeSourceRoots = new ArrayList<String>();
    private final List<String> includeSourceRoots = new ArrayList<String>();
    private final List<String> includePatterns = new ArrayList<String>();
    private final List<String> excludePatterns = new ArrayList<String>();
    
    private final String prefix;
    
    private MavenPluginConfigurationTranslator(
            final MavenProject mavenProject,
            final MavenPluginWrapper mavenPlugin,
            final URI basedirUri,
            final String prefix) {
        this.console = MavenPlugin.getDefault().getConsole();
        this.mavenProject = mavenProject;
        this.basedirUri = basedirUri;
        this.prefix = prefix;
        this.cfgExtractor = MavenPluginConfigurationExtractor.newInstance(mavenPlugin);
    }

    public List<String> getRulesets() {
        return this.cfgExtractor
            .childValues(null, "rulesets", "ruleset");
    }
    
    /**
     * Get the {@literal includeTests} element value if present in the configuration.
     * 
     * @return the value of the {@code includeTests} element.
     */
    public boolean getIncludeTests () {
        return this.cfgExtractor
            .asBoolean(null, "includeTests");
    }

    public List<String> getExcludeRoots() {
        return ImmutableList.copyOf(this.excludeSourceRoots);
    }
    
    public List<String> getIncludeRoots() {
        return ImmutableList.copyOf(this.includeSourceRoots);
    }
    
    public List<String> getIncludes() {
        return ImmutableList.copyOf(this.includePatterns);
    }

    public List<String> getExcludes() {
        return ImmutableList.copyOf(this.excludePatterns);
    }

    private List<String> getExcludePatterns() {
        final List<String> patterns = this.cfgExtractor
            .childValues(null, "excludes", "exclude");
        final List<String> transformedPatterns = new LinkedList<String>();
        if (patterns != null && patterns.size() > 0) {
            for (String p : patterns) {
                p = StringUtils.strip(p);
                if (StringUtils.isBlank(p)) {
                    continue;
                }
                transformedPatterns.add(this.getTransformedPattern(p, "EXCLUDE"));
            }
        }
        return transformedPatterns;
    }
    
    /**
     * Return the included patterns if any.
     * 
     * <p>
     * IMPORTANT:
     *  To line up with the behavior of maven-pmd-plugin includes, if an include
     *  pattern is specified then, all the includeSourceRoots are added to the
     *  excludeSourceRoots. What this means is, there is no point in specifying
     *  excludes at this point.
     * </p>
     * @return a {@code List} of inclusive patterns.
     */
    private List<String> getIncludePatterns() {
        final List<String> patterns = this.cfgExtractor
            .childValues(null, "includes", "include");
        final List<String> transformedPatterns = new LinkedList<String>();
        if (patterns != null && patterns.size() > 0) {
            for (String p : patterns) {
                p = StringUtils.strip(p);
                if (StringUtils.isBlank(p)) {
                    continue;
                }
                transformedPatterns.add(this.getTransformedPattern(p, "INCLUDE"));
            }
        }
        return transformedPatterns;
    }

    private String getTransformedPattern(final String antStylePattern, 
            final String msg) {
        String newPattern;
        if (PATTERNS_CACHE.containsKey(antStylePattern)) {
            newPattern = PATTERNS_CACHE.get(antStylePattern);
        } else {
            newPattern = this.convertAntStylePatternToPmdPattern(antStylePattern);
            PATTERNS_CACHE.put(antStylePattern, newPattern);
            this.console.logMessage(String.format(
                    "[%s]: Transformed %s pattern [%s] -> [%s]",
                    this.prefix, msg, antStylePattern, newPattern));
        }
        return newPattern;
    }
    
    /**
     * Helper to convert the maven-pmd-plugin includes/excludes pattern
     * to PMD pattern (not eclipse PMD).
     * 
     * @param pattern the maven-pmd-plugin pattern.
     * @return the converted PMD pattern.
     */
    private String convertAntStylePatternToPmdPattern(final String pattern) {
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
            } else {
                sb.append(curChar);
            }
        }
        return sb.toString();
    }
    
    private void buildExcludeAndIncludeSourceRoots() {
        final List<File> includeRoots = new ArrayList<File>();
        final List<File> excludeRoots = new ArrayList<File>();
        
        includeRoots.addAll(
            this.transformResourceStringsToFiles(this.mavenProject.getCompileSourceRoots())
        );
        
        //Get all the normalized test roots and add them to include or exclude.
        final List<File> testCompileSourceRoots = 
            this.transformResourceStringsToFiles(this.mavenProject.getTestCompileSourceRoots());
        if (this.getIncludeTests()) {
            includeRoots.addAll(testCompileSourceRoots);
        } else {
            excludeRoots.addAll(testCompileSourceRoots);
        }
        
        // now we need to filter out any excludeRoots from plugin configurations
        final List<File> excludeRootsFromConfig = 
            this.transformResourceStringsToFiles(this.cfgExtractor
                    .childValues(null, "excludeRoots", "excludeRoot")
            );
        // do the filtering
        List<File> filteredIncludeRoots = new LinkedList<File>();
        for (File f : includeRoots) {
            int idx = excludeRootsFromConfig.indexOf(f);
            if (f.isDirectory() && (idx == -1)) {
                filteredIncludeRoots.add(f);
            } else {
                excludeRoots.add(excludeRootsFromConfig.get(idx));
                this.console.logMessage(String.format(
                    "[%s]: As per plugin excludeRoot element,"
                    + " discarding all sources from [%s]", this.prefix, f));
            }
        }
        this.includeSourceRoots.addAll(
                this.convertFileFoldersToRelativePathStrings(filteredIncludeRoots));
        this.excludeSourceRoots.addAll(
                this.convertFileFoldersToRelativePathStrings(excludeRoots));
    }

    private List<String> convertFileFoldersToRelativePathStrings(
            final Iterable<? extends File> sources) {
        final List<String> folders = new ArrayList<String>();
        //No null check as internally we *know*
        for (File f: sources) {
            String relativePath = URIUtils.resolve(f.toURI(), this.basedirUri).getPath();
                //TODO this.basedirUri.relativize(f.toURI()).getPath();
            if (relativePath.endsWith("/")) {
                relativePath = relativePath.substring(0, relativePath.length() - 1);
            } 
            //we append the .* pattern regardless
            relativePath = relativePath + ".*";
            folders.add(relativePath);
        }
        return folders;
    }
    
    private List<File> transformResourceStringsToFiles(final List<String> srcDirNames) {
        final File basedir = this.mavenProject.getBasedir();
        final List<File> sourceDirectories = new ArrayList<File>();
        if (srcDirNames != null) {
            for (String srcDirName : srcDirNames) {
                File srcDir = new File(srcDirName);
                if (!srcDir.isAbsolute()) {
                    srcDir = new File(basedir, srcDir.getPath());
                }
                sourceDirectories.add(srcDir);
            }
        }
        return sourceDirectories;
    }

    private void initialize() {
        // Step 1). Get the included and excluded source roots setup.
        this.buildExcludeAndIncludeSourceRoots();
        // Step 2). Populate includes
        this.includePatterns.clear();
        this.includePatterns.addAll(this.getIncludePatterns());
        // Step 3). Populate excludes
        this.excludePatterns.clear();
        this.excludePatterns.addAll(this.getExcludePatterns());
    }
    
    public static MavenPluginConfigurationTranslator newInstance(
            final MavenProject mavenProject,
            final MavenPluginWrapper mavenPlugin,
            final IProject project,
            final String prefix) {
        final MavenPluginConfigurationTranslator instance = 
            new MavenPluginConfigurationTranslator(
                    mavenProject, mavenPlugin,
                    project.getLocationURI(), 
                    prefix);
        instance.initialize();
        return instance;
    }
}
