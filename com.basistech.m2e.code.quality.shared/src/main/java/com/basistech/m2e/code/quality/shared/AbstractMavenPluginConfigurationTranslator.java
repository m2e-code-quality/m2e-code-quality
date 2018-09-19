package com.basistech.m2e.code.quality.shared;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

import com.google.common.base.Preconditions;

public class AbstractMavenPluginConfigurationTranslator {

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
	 * This the only reference I could find on how the Findbugs Eclipse Plugin
	 * configuration works.
	 * </p>
	 *
	 * @param resc
	 *            the resource location as read from the plugin configuration.
	 * @param newLocation
	 *            the new location relative to the project root.
	 * @throws NullPointerException
	 *             If any of the arguments are {@code null}.
	 * @throws ConfigurationException
	 *             If an error occurred during the resolution of the resource or
	 *             copy to the new location failed.
	 */
	protected void copyUrlResourceToProject(final String resc,
	        final String newLocation) {
		Preconditions.checkNotNull(resc);
		Preconditions.checkNotNull(newLocation);
		final URL urlResc = resolveLocation(resc);
		if (urlResc == null) {
			throw new ConfigurationException(String.format(
			        "could not locate resource [%s]", resc));
		}
		// copy the file to new location
		final File newLocationFile =
		        new File(this.project.getLocationURI().getPath(), newLocation);
		try (InputStream inputStream = urlResc.openStream()) {
			copyIfChanged(inputStream, newLocationFile.toPath());
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
