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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
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
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginConfigurationTranslator;
import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

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

	private static final String CHECKSTYLE_DEFAULT_CONFIG_FILE_NAME = "sun_checks.xml";
	private static final String CHECKSTYLE_DEFAULT_CONFIG_LOCATION =
	        "config/" + CHECKSTYLE_DEFAULT_CONFIG_FILE_NAME;
	/** checkstyle maven plugin artifactId */
	private static final Map<String, String> PATTERNS_CACHE = new HashMap<>();
	private static final String CHECKSTYLE_DEFAULT_CONFIG_FILE_HEADER =
			"<?xml version=\"1.0\"?>\n" +
	        "<!DOCTYPE module PUBLIC \"-//Checkstyle//DTD Checkstyle Configuration 1.3//EN\" " +
			"\"https://checkstyle.org/dtds/configuration_1_3.dtd\">";

	private final URI basedirUri;
	private final Path workingDirectory;
	private final String projectName;

	private MavenPluginConfigurationTranslator(final IMaven maven,
	        final MavenSession session, final MavenProject mavenProject,
	        final MojoExecution mojoExecution, final IProject project,
	        final URI basedirUri, final Path workingDirectory,
	        final IProgressMonitor monitor) throws CoreException {
		super(maven, session, mavenProject, mojoExecution, project, monitor);
		this.projectName = project.getName();
		this.workingDirectory = workingDirectory;
		this.basedirUri = basedirUri;
	}

	public boolean isSkip() throws CoreException {
		return getParameterValue("skip", Boolean.class, Boolean.FALSE);
	}

	public URL getRuleset() throws CheckstylePluginException, CoreException {
		final String configLocation = this.getConfigLocation();
		URL ruleset = getInlineRules();

		if (ruleset != null && !CHECKSTYLE_DEFAULT_CONFIG_FILE_NAME.equals(configLocation)) {
			throw new CheckstylePluginException("If you use inline configuration for rules, don't specify a configLocation");
		}

		if (ruleset == null) {
			ruleset = resolveLocation(this.getConfigLocation());
		}
		if (ruleset == null) {
			throw new CheckstylePluginException(
			        "Failed to resolve RuleSet from inlineConfig or configLocation,SKIPPING Eclipse checkstyle configuration");
		}
		return ruleset;
	}

	private URL getInlineRules() throws CoreException, CheckstylePluginException {
		PlexusConfiguration config = getParameterValue("checkstyleRules", XmlPlexusConfiguration.class);
		if (config == null) {
			return null;
		}
		if (config.getChildCount() > 1) {
			throw new CheckstylePluginException("Currently only one root module is supported");
		}
		if ((config = config.getChild(0)) == null) {
			return null;
		}
		String configFileHeader = getParameterValue("checkstyleRulesHeader", String.class);
		if (configFileHeader == null) {
			configFileHeader = CHECKSTYLE_DEFAULT_CONFIG_FILE_HEADER;
		}
		
		IPath stateLocation = Activator.getDefault().getStateLocation();
		File dir = stateLocation.toFile();
		File checkstyleFile = new File(dir, projectName + "_inline_checkstyle.xml");
		try (Writer writer = new FileWriter(checkstyleFile)){
			writer.write(configFileHeader);
			writer.write(config.toString());
			return checkstyleFile.toURI().toURL();
		} catch (IOException e) {
			CheckstylePluginException.rethrow(e, "Error while extracting inline rules.");
			return null;
		}
	}

	public String getHeaderFile()
	        throws CheckstylePluginException, CoreException {
		final URL headerLocation = getHeaderLocation();
		if (headerLocation == null) {
			return null;
		}
		final Path headerFile = workingDirectory
		        .resolve("checkstyle-header-" + sanitizeFilename(getExecutionId()) + ".txt");
		try (InputStream inputStream = headerLocation.openStream()) {
			copyIfChanged(inputStream, headerFile);
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
		        "checkstyle-suppressions-" + sanitizeFilename(getExecutionId()) + ".xml");
		try (InputStream inputStream = suppressionsLocation.openStream()) {
			copyIfChanged(inputStream, suppressionsFile);
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
		        new FileSet("java-sources-" + sanitizeFilename(getExecutionId()), checkCfg);
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
		return resolveLocation(headerLocation);
	}

	private URL getSuppressionsLocation() throws CoreException {
		String suppressionsLocation =
		        getParameterValue("suppressionsLocation", String.class);
		if (suppressionsLocation == null) {
			suppressionsLocation =
			        getParameterValue("suppressionsFile", String.class);
		}
		if (suppressionsLocation == null) {
			return null;
		}
		return resolveLocation(suppressionsLocation);
	}

	private List<String> getSourceDirectories() throws CoreException {
		final List<String> sourceDirectories = new ArrayList<>(
		        getParameterList("sourceDirectories", String.class));
		final String sourceDirectory =
		        getParameterValue("sourceDirectory", String.class);
		if (sourceDirectory != null) {
			sourceDirectories.add(sourceDirectory);
		}
		if (sourceDirectories.isEmpty()) {
			sourceDirectories.addAll(getMavenProject().getCompileSourceRoots());
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
		if (testSourceDirectories.isEmpty()) {
			testSourceDirectories.addAll(getMavenProject().getTestCompileSourceRoots());
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
			for (final Resource resource : getMavenProject().getBuild()
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
			for (final Resource resource : getMavenProject().getBuild()
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
			for (final Resource resource : getMavenProject().getBuild()
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
			for (final Resource resource : getMavenProject().getBuild()
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
			final URL url = resolveLocation(propertiesLocation);
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
	        final AbstractMavenPluginProjectConfigurator<?> configurator,
	        final MavenProject mavenProject,
	        final MavenPluginWrapper mavenPlugin, final IProject project,
	        final IProgressMonitor monitor, final MavenSession session)
	        throws CoreException {
		final List<MavenPluginConfigurationTranslator> m2csConverters =
		        new ArrayList<>();
		for (final MojoExecution execution : mavenPlugin.getMojoExecutions()) {
			final Path path = project.getWorkingLocation(configurator.getId())
			        .toFile().toPath();
			m2csConverters.add(new MavenPluginConfigurationTranslator(maven,
			        session, mavenProject, execution, project,
			        project.getLocationURI(), path, monitor));
		}
		return m2csConverters;
	}

}
