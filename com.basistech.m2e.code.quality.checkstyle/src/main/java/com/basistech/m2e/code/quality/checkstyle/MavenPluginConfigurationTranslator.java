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
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginConfigurationTranslator;
import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;

import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.projectconfig.FileMatchPattern;
import net.sf.eclipsecs.core.projectconfig.FileSet;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationWorkingCopy;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

/**
 * Utility class to get checkstyle plugin configuration.
 */
public class MavenPluginConfigurationTranslator
        extends AbstractMavenPluginConfigurationTranslator {

	private static final Logger LOG =
	        LoggerFactory.getLogger(MavenPluginConfigurationTranslator.class);

	private static final String CHECKSTYLE_DEFAULT_CONFIG_LOCATION =
	        "config/sun_checks.xml";
	/** checkstyle maven plugin artifactId */
	private static final Map<String, String> PATTERNS_CACHE = new HashMap<>();

	private final MavenProject mavenProject;
	private final URI basedirUri;
	private final ResourceResolver resourceResolver;
	private final MojoExecution execution;
	private final Path workingDirectory;

	private MavenPluginConfigurationTranslator(final IMaven maven,
	        final MavenProject mavenProject, final MojoExecution mojoExecution,
	        final URI basedirUri, final IProgressMonitor monitor,
	        final ResourceResolver resourceResolver,
	        final Path workingDirectory) throws CoreException {
		super(maven, mavenProject, mojoExecution, monitor);
		this.mavenProject = mavenProject;
		this.workingDirectory = workingDirectory;
		this.basedirUri = basedirUri;
		this.resourceResolver = resourceResolver;
		this.execution = mojoExecution;
	}

	public boolean isSkip() throws CoreException {
		return getParameterValue("skip", Boolean.class, Boolean.FALSE);
	}

	public URL getRuleset() throws CheckstylePluginException, CoreException {
		final URL ruleset =
		        this.resourceResolver.resolveLocation(this.getConfigLocation());
		if (ruleset == null) {
			throw new CheckstylePluginException(
			        "Failed to resolve RuleSet from configLocation,SKIPPING Eclipse checkstyle configuration");
		}
		return ruleset;
	}

	public String getHeaderFile()
	        throws CheckstylePluginException, CoreException {
		final URL headerLocation = getHeaderLocation();
		if (headerLocation == null) {
			return null;
		}
		final Path headerFile = workingDirectory
		        .resolve("checkstyle-header-" + getExecutionId() + ".txt");
		try (InputStream inputStream = headerLocation.openStream()) {
			Files.copy(inputStream, headerFile,
			        StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			LOG.error("Could not copy header file {}", headerLocation, e);
			throw new CheckstylePluginException(
			        "Failed to copy header file, SKIPPING Eclipse checkstyle configuration");
		}
		return headerFile.toAbsolutePath().toString();
	}

	public String getSuppressionsFile()
	        throws CheckstylePluginException, CoreException {
		final URL suppressionsLocation = getSuppressionsLocation();
		if (suppressionsLocation == null) {
			return null;
		}

		final Path suppressionsFile = workingDirectory.resolve(
		        "checkstyle-suppressions-" + getExecutionId() + ".xml");
		try (InputStream inputStream = suppressionsLocation.openStream()) {
			Files.copy(inputStream, suppressionsFile,
			        StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			LOG.error("Could not copy suppressions file {}",
			        suppressionsLocation, e);
			throw new CheckstylePluginException(
			        "Failed to copy suppressions file, SKIPPING Eclipse checkstyle configuration");
		}
		return suppressionsFile.toAbsolutePath().toString();
	}

	public String getSuppressionsFileExpression() throws CoreException {
		return getParameterValue("suppressionsFileExpression", String.class,
		        "checkstyle.suppressions.file");
	}

	public void updateCheckConfigWithIncludeExcludePatterns(
	        final ProjectConfigurationWorkingCopy pcWorkingCopy,
	        final ICheckConfiguration checkCfg)
	        throws CheckstylePluginException, CoreException {
		final FileSet fs =
		        new FileSet("java-sources-" + getExecutionId(), checkCfg);
		fs.setEnabled(true);
		// add fileset includes/excludes
		fs.setFileMatchPatterns(getIncludesExcludesFileMatchPatterns());
		// now add the config
		pcWorkingCopy.getFileSets().add(fs);
	}

	/**
	 * Get the {@literal propertiesLocation} element if present in the
	 * configuration.
	 * 
	 * @return the value of the {@code propertyExpansion} element.
	 * @throws CoreException
	 *             if an error occurs
	 */
	private String getPropertiesLocation() throws CoreException {
		return getParameterValue("propertiesLocation", String.class);
	}

	/**
	 * Get the {@literal propertyExpansion} element if present in the
	 * configuration.
	 * 
	 * @return the value of the {@code propertyExpansion} element.
	 * @throws CoreException
	 *             if an error occurs
	 */
	private String getPropertyExpansion() throws CoreException {
		return getParameterValue("propertyExpansion", String.class);
	}

	/**
	 * Get the {@literal includeTestSourceDirectory} element value if present in
	 * the configuration.
	 * 
	 * @return the value of the {@code includeTestSourceDirectory} element.
	 * @throws CoreException
	 *             if an error occurs
	 */
	private boolean isIncludeTestSourceDirectory() throws CoreException {
		return getParameterValue("includeTestSourceDirectory", Boolean.class,
		        Boolean.FALSE);
	}

	private boolean isIncludeResources() throws CoreException {
		return getParameterValue("includeResources", Boolean.class,
		        Boolean.TRUE);
	}

	private boolean isIncludeTestResources() throws CoreException {
		return getParameterValue("includeTestResources", Boolean.class,
		        Boolean.TRUE);
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
		return getParameterValue("configLocation", String.class,
		        CHECKSTYLE_DEFAULT_CONFIG_LOCATION);
	}

	private URL getHeaderLocation() throws CoreException {
		String headerLocation = getParameterValue("headerLocation",
		        String.class, "LICENSE.txt");
		if ("config/maven_checks.xml".equals(getConfigLocation())
		        && "LICENSE.txt".equals(headerLocation)) {
			headerLocation = "config/maven-header.txt";
		}
		return resourceResolver.resolveLocation(headerLocation);
	}

	private URL getSuppressionsLocation() throws CoreException {
		String suppressionsLocation =
		        getParameterValue("suppressionsLocation", String.class);
		if (suppressionsLocation == null) {
			suppressionsLocation =
			        getParameterValue("suppressionsFile", String.class);
		}
		return this.resourceResolver.resolveLocation(suppressionsLocation);
	}

	private List<String> getSourceDirectories() throws CoreException {
		final List<String> sourceDirectories = new ArrayList<>(
		        getParameterList("sourceDirectories", String.class));
		final String sourceDirectory =
		        getParameterValue("sourceDirectory", String.class);
		if (sourceDirectory != null) {
			sourceDirectories.add(sourceDirectory);
		}
		return sourceDirectories;
	}

	private List<String> getTestSourceDirectories() throws CoreException {
		final List<String> testSourceDirectories = new ArrayList<>(
		        getParameterList("testSourceDirectories", String.class));
		final String testSourceDirectory =
		        getParameterValue("testSourceDirectory", String.class);
		if (testSourceDirectory != null) {
			testSourceDirectories.add(testSourceDirectory);
		}
		return testSourceDirectories;
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

	/**
	 * 
	 * @return A list of {@code FileMatchPattern}'s.
	 * @throws CheckstylePluginException
	 *             if an error occurs getting the include exclude patterns.
	 * @throws CoreException
	 */
	private List<FileMatchPattern> getIncludesExcludesFileMatchPatterns()
	        throws CheckstylePluginException, CoreException {
		final List<FileMatchPattern> patterns = new LinkedList<>();

		/**
		 * Step 1). Get all the source roots (including test sources root, if
		 * enabled).
		 */
		final Set<String> sourceFolders = new HashSet<>(getSourceDirectories());
		if (isIncludeTestSourceDirectory()) {
			sourceFolders.addAll(getTestSourceDirectories());
		}

		/**
		 * Step 2). Get all the includes patterns add them for all source roots.
		 * NOTES: - Since the eclipse-cs excludes override (or more correctly
		 * the later ones) the includes, we make sure that if no include
		 * patterns are specified we at least put the source folders. - Also, we
		 * use relative path to project root.
		 */
		final List<String> includePatterns = this.getIncludes();
		for (final String folder : sourceFolders) {
			final String folderRelativePath = relativize(folder);
			if (!includePatterns.isEmpty()) {
				patterns.addAll(
				        this.normalizePatternsToCheckstyleFileMatchPattern(
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
		for (final String folder : sourceFolders) {
			final String folderRelativePath = relativize(folder);
			patterns.addAll(this.normalizePatternsToCheckstyleFileMatchPattern(
			        excludePatterns, this.convertToEclipseCheckstyleRegExpPath(
			                folderRelativePath),
			        false));
		}

		if (this.isIncludeResources()) {
			final List<String> resourceIncludePatterns =
			        this.getResourceIncludes();
			for (final Resource resource : this.mavenProject.getBuild()
			        .getResources()) {
				final String folderRelativePath =
				        relativize(resource.getDirectory());
				patterns.addAll(
				        this.normalizePatternsToCheckstyleFileMatchPattern(
				                resourceIncludePatterns, folderRelativePath,
				                true));
			}

			final List<String> resourceExcludePatterns =
			        this.getResourceExcludes();
			for (final Resource resource : this.mavenProject.getBuild()
			        .getResources()) {
				final String folderRelativePath =
				        relativize(resource.getDirectory());
				patterns.addAll(
				        this.normalizePatternsToCheckstyleFileMatchPattern(
				                resourceExcludePatterns, folderRelativePath,
				                false));
			}
		}

		if (this.isIncludeTestResources()) {
			final List<String> resourceIncludePatterns =
			        this.getResourceIncludes();
			for (final Resource resource : this.mavenProject.getBuild()
			        .getTestResources()) {
				if (!resource.getExcludes().isEmpty()
				        || !resource.getIncludes().isEmpty()) {
					// ignore resources that have ex/includes for now
					continue;
				}
				final String folderRelativePath = "^" + this.basedirUri
				        .relativize(new File(resource.getDirectory()).toURI())
				        .getPath();
				patterns.addAll(
				        this.normalizePatternsToCheckstyleFileMatchPattern(
				                resourceIncludePatterns, folderRelativePath,
				                true));
			}

			final List<String> resourceExcludePatterns =
			        this.getResourceExcludes();
			for (final Resource resource : this.mavenProject.getBuild()
			        .getTestResources()) {
				if (!resource.getExcludes().isEmpty()
				        || !resource.getIncludes().isEmpty()) {
					// ignore resources that have ex/includes for now
					continue;
				}
				final String folderRelativePath = "^" + this.basedirUri
				        .relativize(new File(resource.getDirectory()).toURI())
				        .getPath();
				patterns.addAll(
				        this.normalizePatternsToCheckstyleFileMatchPattern(
				                resourceExcludePatterns, folderRelativePath,
				                false));
			}
		}

		return patterns;
	}

	private String relativize(final String folder) {
		String folderRelativePath = "^";
		File file = new File(folder);
		if (file.isAbsolute()) {
			folderRelativePath +=
			        this.basedirUri.relativize(file.toURI()).getPath();
		} else {
			folderRelativePath += folder;
		}
		return folderRelativePath;
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
		final List<FileMatchPattern> fileMatchPatterns = new LinkedList<>();
		for (final String p : patterns) {
			final FileMatchPattern fmp = new FileMatchPattern(
			        String.format("%s%s", relativePath, p));
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
	private List<String> getPatterns(final String elemName)
	        throws CoreException {
		final List<String> transformedPatterns = new LinkedList<>();
		final String patternsString = getParameterValue(elemName, String.class);
		if (patternsString == null || patternsString.isEmpty()) {
			return transformedPatterns;
		}
		final String[] patternsArray = StringUtils.split(patternsString, ",");
		for (String p : patternsArray) {
			p = StringUtils.strip(p);
			if (p == null || p.isEmpty()) {
				continue;
			}
			String csPattern;
			if (PATTERNS_CACHE.containsKey(p)) {
				csPattern = PATTERNS_CACHE.get(p);
			} else {
				csPattern = CheckstyleUtil
				        .convertAntStylePatternToCheckstylePattern(p);
				PATTERNS_CACHE.put(p, csPattern);
			}
			transformedPatterns.add(csPattern);
		}
		return transformedPatterns;
	}

	public Properties getConfiguredProperties()
	        throws CoreException, CheckstylePluginException {
		final String propertiesLocation = getPropertiesLocation();
		final Properties properties = new Properties();
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
			} catch (final IOException e) {
				throw new CheckstylePluginException(String.format(
				        "Failed to LOAD properties from propertiesLocation [%s]",
				        propertiesLocation));
			}
		}
		return properties;
	}

	public void updatePropertiesWithPropertyExpansion(final Properties props)
	        throws CheckstylePluginException, CoreException {
		final String propertyExpansion = this.getPropertyExpansion();
		if (propertyExpansion != null) {
			try {
				// beware of windows path separator
				final String escapedPropertyExpansion =
				        StringEscapeUtils.escapeJava(propertyExpansion);
				props.load(new StringReader(escapedPropertyExpansion));
			} catch (final IOException e) {
				throw new CheckstylePluginException(String.format(
				        "[%s]: Failed to checkstyle propertyExpansion [%s]",
				        CheckstyleEclipseConstants.LOG_PREFIX,
				        propertyExpansion));
			}
		}
	}

	public static List<MavenPluginConfigurationTranslator> newInstance(
	        final IMaven maven,
	        final AbstractMavenPluginProjectConfigurator configurator,
	        final MavenProject mavenProject,
	        final MavenPluginWrapper mavenPlugin, final IProject project,
	        final IProgressMonitor monitor, final MavenSession session)
	        throws CoreException {
		final List<MavenPluginConfigurationTranslator> m2csConverters =
		        new ArrayList<>();
		for (final MojoExecution execution : mavenPlugin.getMojoExecutions()) {
			final ResourceResolver resourceResolver =
			        AbstractMavenPluginProjectConfigurator.getResourceResolver(
			                execution, session, project.getLocation());
			final Path path = project.getWorkingLocation(configurator.getId())
			        .toFile().toPath();
			m2csConverters.add(new MavenPluginConfigurationTranslator(maven,
			        mavenProject, execution, project.getLocationURI(), monitor,
			        resourceResolver, path));
		}
		return m2csConverters;
	}

}
