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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginConfigurationTranslator;
import com.google.common.collect.ImmutableList;

/**
 * Utility class to get maven-pmd-plugin configuration.
 */
public class MavenPluginConfigurationTranslator extends AbstractMavenPluginConfigurationTranslator {

	private static final Map<String, String> PATTERNS_CACHE = new HashMap<>();

	private final URI basedirUri;
	private final List<String> excludeSourceRoots = new ArrayList<>();
	private final List<String> includeSourceRoots = new ArrayList<>();
	private final List<String> includePatterns = new ArrayList<>();
	private final List<String> excludePatterns = new ArrayList<>();
	private final MojoExecution checkExecution;
	private final ConfigurationContainer checkConfiguration;

	private MavenPluginConfigurationTranslator(
	        final IMaven maven,
	        final MavenSession session, final MavenProject mavenProject,
	        final MojoExecution execution, final MojoExecution forkedExecution,
	        final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		super(maven, session, mavenProject, forkedExecution, project, monitor);
		this.basedirUri = project.getLocationURI();
		this.checkExecution = execution;
		checkConfiguration = new PluginExecution();
		checkConfiguration.setConfiguration(checkExecution.getConfiguration());
	}

	public List<String> getRulesets() throws CoreException {
		final String[] rulesets = getParameterValue("rulesets", String[].class);
		if (rulesets == null) {
			// no special rulesets configured - use the same defaults as the
			// maven-pmd-plugin does
			return Arrays.asList("/rulesets/java/basic.xml",
			        "/rulesets/java/unusedcode.xml",
			        "/rulesets/java/imports.xml");
		}
		return Arrays.asList(rulesets);
	}

	private List<String> getExcludePatterns() throws CoreException {
		final String[] excludes = getParameterValue("excludes", String[].class);
		final List<String> transformedPatterns = new LinkedList<>();
		if (excludes != null && excludes.length > 0) {
			for (String p : excludes) {
				p = StringUtils.strip(p);
				if (StringUtils.isBlank(p)) {
					continue;
				}
				transformedPatterns.add(this.getTransformedPattern(p));
			}
		}
		return transformedPatterns;
	}

	/**
	 * Return the included patterns if any.
	 * <p>
	 * IMPORTANT: To line up with the behavior of maven-pmd-plugin includes, if
	 * an include pattern is specified then, all the includeSourceRoots are
	 * added to the excludeSourceRoots. What this means is, there is no point in
	 * specifying excludes at this point.
	 * </p>
	 * 
	 * @return a {@code List} of inclusive patterns.
	 * @throws CoreException
	 */
	private List<String> getIncludePatterns() throws CoreException {
		final String[] includes = getParameterValue("includes", String[].class);
		final List<String> transformedPatterns = new LinkedList<>();
		if (includes != null && includes.length > 0) {
			for (String p : includes) {
				p = StringUtils.strip(p);
				if (StringUtils.isBlank(p)) {
					continue;
				}
				transformedPatterns.add(this.getTransformedPattern(p));
			}
		}
		return transformedPatterns;
	}

	public boolean isSkip() throws CoreException {
		return Boolean.TRUE.equals(
				getParameterValue(checkExecution, checkConfiguration, "skip", Boolean.class));
	}

	/**
	 * Get the {@literal includeTests} element value if present in the
	 * configuration.
	 * 
	 * @return the value of the {@code includeTests} element.
	 * @throws CoreException
	 *             if an error occurs
	 */
	public boolean getIncludeTests() throws CoreException {
		final Boolean tests = getParameterValue("includeTests", Boolean.class);
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

	private String getTransformedPattern(final String antStylePattern) {
		String newPattern;
		if (PATTERNS_CACHE.containsKey(antStylePattern)) {
			newPattern = PATTERNS_CACHE.get(antStylePattern);
		} else {
			newPattern =
			        this.convertAntStylePatternToPmdPattern(antStylePattern);
			PATTERNS_CACHE.put(antStylePattern, newPattern);
		}
		return newPattern;
	}

	/**
	 * Helper to convert the maven-pmd-plugin includes/excludes pattern to PMD
	 * pattern (not eclipse PMD).
	 * 
	 * @param pattern
	 *            the maven-pmd-plugin pattern.
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
		final List<File> includeRoots = new ArrayList<>();
		final List<File> excludeRoots = new ArrayList<>();

		includeRoots.addAll(this.transformResourceStringsToFiles(
		        getMavenProject().getCompileSourceRoots()));

		final List<String> targetDirectories = new ArrayList<>();
		targetDirectories.add(getMavenProject().getBuild().getDirectory());
		excludeRoots.addAll(
		        this.transformResourceStringsToFiles(targetDirectories));

		// Get all the normalized test roots and add them to include or exclude.
		final List<File> testCompileSourceRoots =
		        this.transformResourceStringsToFiles(
		                getMavenProject().getTestCompileSourceRoots());
		if (this.getIncludeTests()) {
			includeRoots.addAll(testCompileSourceRoots);
		} else {
			excludeRoots.addAll(testCompileSourceRoots);
		}

		// now we need to filter out any excludeRoots from plugin configurations
		List<File> excludeRootsFromConfig;
		final File[] excludeRootsArray = getParameterValue("excludeRoots", File[].class);
		if (excludeRootsArray == null) {
			excludeRootsFromConfig = Collections.emptyList();
		} else {
			excludeRootsFromConfig = Arrays.asList(excludeRootsArray);
		}
		excludeRoots.addAll(excludeRootsFromConfig);

		// do the filtering
		final List<File> filteredIncludeRoots = new LinkedList<>();
		for (final File f : includeRoots) {
			final int idx = excludeRootsFromConfig.indexOf(f);
			/**
			 * Be optimistic when adding inclusions; if the specified File does
			 * not exist yet, then assume it will at some point and include it.
			 */
			if (idx == -1 && (f.isDirectory() || !f.exists())) {
				filteredIncludeRoots.add(f);
			} else {
				// adding in mid-iteration?
				excludeRoots.add(f);
			}
		}
		this.includeSourceRoots.addAll(this
		        .convertFileFoldersToRelativePathStrings(filteredIncludeRoots));
		this.excludeSourceRoots.addAll(
		        this.convertFileFoldersToRelativePathStrings(excludeRoots));
	}

	private List<String> convertFileFoldersToRelativePathStrings(
	        final Iterable<? extends File> sources) {
		final List<String> folders = new ArrayList<>();
		// No null check as internally we *know*
		for (final File f : sources) {
			String relativePath;
			if (!f.isAbsolute()) {
				relativePath = this.basedirUri.resolve(f.toURI()).getPath();
			} else {
				relativePath = f.getAbsolutePath();
			}

			if ("\\".equals(File.separator)) {
				relativePath = relativePath.replace("\\", "/");
			}
			if (relativePath.endsWith("/")) {
				relativePath =
				        relativePath.substring(0, relativePath.length() - 1);
			}
			// we append the .* pattern regardless
			relativePath = relativePath + ".*";
			folders.add(relativePath);
		}
		return folders;
	}

	private List<File> transformResourceStringsToFiles(
	        final List<String> srcDirNames) {
		final File basedir = getMavenProject().getBasedir();
		final List<File> sourceDirectories = new ArrayList<>();
		if (srcDirNames != null) {
			for (final String srcDirName : srcDirNames) {
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

	public static MavenPluginConfigurationTranslator newInstance(
	        final IMaven maven,
	        final MavenSession session, final MavenProject mavenProject,
	        final MojoExecution checkGoalExecution, 
	        final MojoExecution pmdGoalExecution, final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		final MavenPluginConfigurationTranslator m2csConverter =
		        new MavenPluginConfigurationTranslator(maven, session,
		                mavenProject, checkGoalExecution, pmdGoalExecution, 
		                project, monitor);
		m2csConverter.initialize();
		return m2csConverter;
	}
}
