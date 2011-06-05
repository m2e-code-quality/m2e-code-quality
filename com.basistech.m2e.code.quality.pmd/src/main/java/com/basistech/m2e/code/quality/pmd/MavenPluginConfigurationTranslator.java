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
package com.basistech.m2e.code.quality.pmd;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.utils.URIUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.google.common.collect.ImmutableList;

/**
 * Utility class to get maven-pmd-plugin configuration.
 */
public class MavenPluginConfigurationTranslator {

    private static final Map<String, String> PATTERNS_CACHE = new HashMap<String, String>();

    private final MavenProject mavenProject;
    private final URI basedirUri;
    private final List<String> excludeSourceRoots = new ArrayList<String>();
    private final List<String> includeSourceRoots = new ArrayList<String>();
    private final List<String> includePatterns = new ArrayList<String>();
    private final List<String> excludePatterns = new ArrayList<String>();

    private IProject project;

    private MavenSession session;

    private AbstractMavenPluginProjectConfigurator configurator;

    private MojoExecution pmdGoalExecution;

    private MavenPluginConfigurationTranslator(final AbstractMavenPluginProjectConfigurator configurator,
                                               final MavenSession session, final MavenProject mavenProject,
                                               final MavenPluginWrapper pluginWrapper,
                                               MojoExecution pmdGoalExecution, final IProject project) throws CoreException {
        this.mavenProject = mavenProject;
        this.project = project;
        this.basedirUri = this.project.getLocationURI();
        this.session = session;
        this.pmdGoalExecution = pmdGoalExecution;
        this.configurator = configurator;
    }

    public List<String> getRulesets() throws CoreException {
        String[] rulesets = configurator.getParameterValue("rulesets", String[].class, session,
                                                           pmdGoalExecution);
        if (rulesets == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(rulesets);
    }

    private List<String> getExcludePatterns() throws CoreException {
        String[] excludes = configurator.getParameterValue("excludes", String[].class, session,
                                                           pmdGoalExecution);
        final List<String> transformedPatterns = new LinkedList<String>();
        if (excludes != null && excludes.length > 0) {
            for (String p : excludes) {
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
     * <p>
     * IMPORTANT: To line up with the behavior of maven-pmd-plugin includes, if an include pattern is
     * specified then, all the includeSourceRoots are added to the excludeSourceRoots. What this means is,
     * there is no point in specifying excludes at this point.
     * </p>
     * 
     * @return a {@code List} of inclusive patterns.
     * @throws CoreException
     */
    private List<String> getIncludePatterns() throws CoreException {
        String[] includes = configurator.getParameterValue("includes", String[].class, session,
                                                           pmdGoalExecution);
        final List<String> transformedPatterns = new LinkedList<String>();
        if (includes != null && includes.length > 0) {
            for (String p : includes) {
                p = StringUtils.strip(p);
                if (StringUtils.isBlank(p)) {
                    continue;
                }
                transformedPatterns.add(this.getTransformedPattern(p, "INCLUDE"));
            }
        }
        return transformedPatterns;
    }

    /**
     * Get the {@literal includeTests} element value if present in the configuration.
     * 
     * @return the value of the {@code includeTests} element.
     * @throws CoreException
     */
    public boolean getIncludeTests() throws CoreException {
        Boolean tests = configurator.getParameterValue("includeTests", Boolean.class, session,
                                                       pmdGoalExecution);
        return tests != null && tests.booleanValue();
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

    private String getTransformedPattern(final String antStylePattern, final String msg) {
        String newPattern;
        if (PATTERNS_CACHE.containsKey(antStylePattern)) {
            newPattern = PATTERNS_CACHE.get(antStylePattern);
        } else {
            newPattern = this.convertAntStylePatternToPmdPattern(antStylePattern);
            PATTERNS_CACHE.put(antStylePattern, newPattern);
        }
        return newPattern;
    }

    /**
     * Helper to convert the maven-pmd-plugin includes/excludes pattern to PMD pattern (not eclipse PMD).
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
            // mark the end so we can append the last char of string
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

    private void buildExcludeAndIncludeSourceRoots() throws CoreException {
        final List<File> includeRoots = new ArrayList<File>();
        final List<File> excludeRoots = new ArrayList<File>();

        includeRoots.addAll(this.transformResourceStringsToFiles(this.mavenProject.getCompileSourceRoots()));

        List<String> targetDirectories = new ArrayList<String>();
        targetDirectories.add(this.mavenProject.getBuild().getDirectory());
        excludeRoots.addAll(this.transformResourceStringsToFiles(targetDirectories));

        // Get all the normalized test roots and add them to include or exclude.
        final List<File> testCompileSourceRoots = this.transformResourceStringsToFiles(this.mavenProject
            .getTestCompileSourceRoots());
        if (this.getIncludeTests()) {
            includeRoots.addAll(testCompileSourceRoots);
        } else {
            excludeRoots.addAll(testCompileSourceRoots);
        }

        // now we need to filter out any excludeRoots from plugin configurations
        List<File> excludeRootsFromConfig;
        File[] excludeRootsArray = configurator.getParameterValue("excludeRoots", File[].class, session,
                                                                  pmdGoalExecution);
        if (excludeRootsArray == null) {
            excludeRootsFromConfig = Collections.emptyList();
        } else {
            excludeRootsFromConfig = Arrays.asList(excludeRootsArray);
        }
        // do the filtering
        List<File> filteredIncludeRoots = new LinkedList<File>();
        for (File f : includeRoots) {
            int idx = excludeRootsFromConfig.indexOf(f);
            /**
             * Be optimistic when adding inclusions; if the specified File does not exist yet, then assume it
             * will at some point and include it.
             */
            if (idx == -1 && (f.isDirectory() || !f.exists())) {
                filteredIncludeRoots.add(f);
            } else {
                // adding in mid-iteration?
                excludeRoots.add(f);
            }
        }
        this.includeSourceRoots.addAll(this.convertFileFoldersToRelativePathStrings(filteredIncludeRoots));
        this.excludeSourceRoots.addAll(this.convertFileFoldersToRelativePathStrings(excludeRoots));
    }

    private List<String> convertFileFoldersToRelativePathStrings(final Iterable<? extends File> sources) {
        final List<String> folders = new ArrayList<String>();
        //No null check as internally we *know*
        for (File f: sources) {
	    String relativePath;
	    if (!f.isAbsolute()) {
		relativePath = URIUtils.resolve(this.basedirUri,f.toURI()).getPath();
                //TODO this.basedirUri.relativize(f.toURI()).getPath();
	    } else {
		relativePath = f.getAbsolutePath();
	    }

	    if ("\\".equals(File.separator)) {
	    	relativePath = relativePath.replace("\\","/");
	    }
            if (relativePath.endsWith("/")) {
                relativePath = relativePath.substring(0, relativePath.length() - 1);
            }
            // we append the .* pattern regardless
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

    private void initialize() throws CoreException {
        // Step 1). Get the included and excluded source roots setup.
        this.buildExcludeAndIncludeSourceRoots();
        // Step 2). Populate includes
        this.includePatterns.clear();
        this.includePatterns.addAll(this.getIncludePatterns());
        // Step 3). Populate excludes
        this.excludePatterns.clear();
        this.excludePatterns.addAll(this.getExcludePatterns());
    }

    public static MavenPluginConfigurationTranslator newInstance(AbstractMavenPluginProjectConfigurator configurator,
                                                                 MavenSession session,
                                                                 final MavenProject mavenProject,
                                                                 final MavenPluginWrapper mavenPlugin,
                                                                 final MojoExecution pmdGoalExecution,
                                                                 final IProject project)
        throws CoreException {
        final MavenPluginConfigurationTranslator m2csConverter = new MavenPluginConfigurationTranslator(
                                                                                                        configurator,
                                                                                                        session,
                                                                                                        mavenProject,
                                                                                                        mavenPlugin,
                                                                                                        pmdGoalExecution,
                                                                                                        project);
        m2csConverter.initialize();
        return m2csConverter;
    }
}
