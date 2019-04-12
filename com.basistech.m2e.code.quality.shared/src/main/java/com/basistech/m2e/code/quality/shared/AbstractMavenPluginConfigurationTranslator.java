package com.basistech.m2e.code.quality.shared;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.embedder.IMaven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class AbstractMavenPluginConfigurationTranslator {
	private static final Logger LOG = LoggerFactory
	        .getLogger(AbstractMavenPluginConfigurationTranslator.class);

	private final IMaven maven;
	private final MavenProject mavenProject;
	private final MojoExecution mojoExecution;
	private final IProgressMonitor monitor;
	private final ConfigurationContainer execution;
	private final IProject project;
	private final ResourceResolver resourceResolver;

	public AbstractMavenPluginConfigurationTranslator(final IMaven maven,
			final MavenSession session, final MavenProject mavenProject,
	        final MojoExecution mojoExecution, final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		this.maven = maven;
		this.mavenProject = mavenProject;
		this.project = project;
		this.mojoExecution = mojoExecution;
		this.monitor = monitor;
		this.resourceResolver = AbstractMavenPluginProjectConfigurator
				.getResourceResolver(mojoExecution, session, project.getLocation());
		execution = new PluginExecution();
		execution.setConfiguration(mojoExecution.getConfiguration());
	}

	protected MavenProject getMavenProject() {
		return mavenProject;
	}

	public <T> T getParameterValue(final String parameter,
	        final Class<T> asType) throws CoreException {
		return getParameterValue(mojoExecution, execution, parameter, asType);
	}

	protected <T> T getParameterValue(final MojoExecution execution,
	        final ConfigurationContainer configurationContainer,
	        final String parameter, final Class<T> asType) throws CoreException {
		return maven.getMojoParameterValue(mavenProject, parameter, asType,
				execution.getPlugin(), configurationContainer, execution.getGoal(),
		        monitor);
	}

	public <T> T getParameterValue(final String parameter,
	        final Class<T> asType, final T defaultValue) throws CoreException {
		final T parameterValue = getParameterValue(parameter, asType);
		if (parameterValue == null) {
			return defaultValue;
		}
		return parameterValue;
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> getParameterList(final String parameter,
	        @SuppressWarnings("unused") final Class<T> asType)
	        throws CoreException {
		ConfigurationContainer executionClone = execution.clone();
		Xpp3Dom configuration = (Xpp3Dom) executionClone.getConfiguration();
		configuration = configuration != null
		        ? configuration.getChild(parameter) : null;
		if (configuration != null && configuration.getChildCount() > 0) {
			configuration.setAttribute("default-value", "");
		}
		final List<T> list = maven.getMojoParameterValue(mavenProject, parameter,
		        List.class, mojoExecution.getPlugin(), executionClone,
		        mojoExecution.getGoal(), monitor);
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}

	/**
	 * Copy a resource from the maven plugin configuration to a location within
	 * the project.
	 * <p>
	 * This the only reference I could find on how the FindBugs Eclipse Plugin
	 * configuration works.
	 * </p>
	 *
	 * @param resc
	 *            the resource location as read from the plugin configuration.
	 * @param newLocation
	 *            the new location relative to the project root.
	 * @return <code>true</code> if resource has been found and copied.
	 * @throws NullPointerException
	 *             If any of the arguments are {@code null}.
	 * @throws ConfigurationException
	 *             If an error occurred during copy to the new location failed.
	 *             If the resolutions fails only an error will be logged
	 */
	protected boolean copyUrlResourceToProject(final String resc,
	        final String newLocation) {
		Preconditions.checkNotNull(resc);
		Preconditions.checkNotNull(newLocation);
		final URL urlResc = resolveLocation(resc);
		if (urlResc == null) {
			LOG.error(String.format("%s: could not locate resource [%s] in classpath of [%s]", project.getName(), resc, mojoExecution));
			return false;
		}
		// copy the file to new location
		final File newLocationFile =
		        new File(this.project.getLocationURI().getPath(), newLocation);
		try (InputStream inputStream = urlResc.openStream()) {
			copyIfChanged(inputStream, newLocationFile.toPath());
			return true;
		} catch (final IOException ex) {
			throw new ConfigurationException(String.format(
			        "could not copy resource [%s] to [%s], reason [%s]",
			        resc, newLocationFile,
			        ex.getLocalizedMessage()), ex);
		}
	}

	/**
	 * Resolve a location from within the plugin configuration
	 * @param resc
	 *            the resource location as read from the plugin configuration.
	 * @return an URL to the requested resource
	 * @throws NullPointerException
	 *             If the argument is {@code null}.
	 */
	protected URL resolveLocation(String resc) {
		Preconditions.checkNotNull(resc);
		return this.resourceResolver.resolveLocation(resc);
	}

	protected void copyIfChanged(InputStream input, Path output) throws IOException {
		InputStream source = input;
		if (Files.exists(output)) {
			byte[] fileContent = IOUtil.toByteArray(input);
			ByteArrayInputStream bufferedInput = new ByteArrayInputStream(fileContent);

			// compare content first
			try (InputStream outputContent = Files.newInputStream(output)) {
				if (IOUtil.contentEquals(bufferedInput, outputContent)) {
					return;
				}
			}

			// rewind input and use it
			bufferedInput.reset();
			source = bufferedInput;
		}
		Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Copy a comma separated list of resource names, separated by comma. Will append an index to the basename if there
	 * is more than a single element. Also, for backwards compatibility the first element will not have an index
	 * appended.
	 * @param resc
	 *            the resource location as read from the plugin configuration. Can be a single resource or a comma 
	 *            separated list of resources
	 * @param newLocationBase
	 *            the new location relative to the project root.
	 * @return a list of new locations relative to the project root
	 * @throws NullPointerException
	 *             If any of the arguments are {@code null}.
	 * @throws ConfigurationException
	 *             If an error occurred during the resolution of the resource or
	 *             copy to the new location failed.
	 * @see #copyUrlResourceToProject(String, String)
	 */
	protected List<String> copyUrlResourcesToProject(String resc, String newLocationBase) {
		Preconditions.checkNotNull(resc);
		Preconditions.checkNotNull(newLocationBase);

		List<String> result = new ArrayList<String>();
		int index=0;
		for (String resource : resc.split(",")) {
			String suffix = index == 0 ? "" : "." + index;
			String target = newLocationBase + suffix; 
			if (copyUrlResourceToProject(resource, target)) {
				result.add(target);
				++index;
			}
		}
		return result;
	}

	public String getExecutionId() {
		return mojoExecution.getExecutionId();
	}

	/**
	 * Make sure a valid filename can be generated from the executionId by only allowing characters, digits, dash,
	 * underscore and dot. All other characters are translated to an underscore.
	 * @param filename
	 *         filename or part thereof that needs sanitation
	 * @return a string that can safely be used as a (part of a) filename
	 */
	protected String sanitizeFilename(String filename) {
		Preconditions.checkNotNull(filename);
		return filename.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
	}
}
