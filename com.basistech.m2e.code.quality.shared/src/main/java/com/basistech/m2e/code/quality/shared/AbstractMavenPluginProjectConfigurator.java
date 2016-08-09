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
package com.basistech.m2e.code.quality.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.ArtifactKey;
import org.eclipse.m2e.core.embedder.IMaven;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.m2e.core.project.MavenProjectChangedEvent;
import org.eclipse.m2e.core.project.configurator.AbstractProjectConfigurator;
import org.eclipse.m2e.core.project.configurator.ProjectConfigurationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * An abstract class merging the required callbacks of
 * {@link AbstractProjectConfigurator}, suitable for maven plugins that have a
 * corresponding Eclipse Plugin, whose configuration can be established by the
 * plugin configuration.
 * 
 */
public abstract class AbstractMavenPluginProjectConfigurator
        extends AbstractProjectConfigurator {

	private static final Logger LOG = LoggerFactory
	        .getLogger(AbstractMavenPluginProjectConfigurator.class);

	public static void removeNature(final IProject project,
	        final String natureId, final IProgressMonitor monitor)
	        throws CoreException {
		removeNature(project, natureId, IResource.KEEP_HISTORY, monitor);
	}

	public static void removeNature(final IProject project,
	        final String natureId, final int updateFlags,
	        final IProgressMonitor monitor) throws CoreException {
		if (project.hasNature(natureId)) {
			final IProjectDescription description = project.getDescription();
			final String[] prevNatures = description.getNatureIds();
			final List<String> natures =
			        new ArrayList<>(Arrays.asList(prevNatures));
			natures.remove(natureId);
			final String[] newNatures = natures.toArray(new String[0]);
			description.setNatureIds(newNatures);
			project.setDescription(description, updateFlags, monitor);
		}
	}

	@Override
	public <T> T getParameterValue(final MavenProject project,
	        final String parameter, final Class<T> asType,
	        final MojoExecution mojoExecution, final IProgressMonitor monitor)
	        throws CoreException {
		return super.getParameterValue(project, parameter, asType,
		        mojoExecution, monitor);
	}

	protected MojoExecution findForkedExecution(final MojoExecution primary,
	        final String groupId, final String artifactId, final String goal) {
		final Map<String, List<MojoExecution>> forkedExecutions =
		        primary.getForkedExecutions();
		MojoExecution goalExecution = null;
		for (final List<MojoExecution> possibleExecutionList : forkedExecutions
		        .values()) {
			for (final MojoExecution possibleExecution : possibleExecutionList) {
				if (groupId.equals(possibleExecution.getGroupId())
				        && artifactId.equals(possibleExecution.getArtifactId())
				        && goal.equals(possibleExecution.getGoal())) {
					goalExecution = possibleExecution;
					break;
				}
			}
			if (goalExecution != null) {
				break;
			}
		}
		return goalExecution;
	}

	@Override
	public void configure(final ProjectConfigurationRequest request,
	        final IProgressMonitor monitor) throws CoreException {
		LOG.debug("configure {}", request.getProject());

		final MavenProject mavenProject = request.getMavenProject();
		if (mavenProject == null) {
			return;
		}

		final MavenPluginWrapper pluginWrapper =
		        this.getMavenPlugin(monitor, request.getMavenProjectFacade());
		final IProject project = request.getProject();

		if (!pluginWrapper.isPluginConfigured()) {
			return;
		}

		@SuppressWarnings("deprecation")
		final MavenSession mavenSession = request.getMavenSession();

		this.handleProjectConfigurationChange(request.getMavenProjectFacade(),
		        project, monitor, pluginWrapper, mavenSession);
	}

	@Override
	public void mavenProjectChanged(
	        final MavenProjectChangedEvent mavenProjectChangedEvent,
	        final IProgressMonitor monitor) throws CoreException {
		final IMavenProjectFacade mavenProjectFacade =
		        mavenProjectChangedEvent.getMavenProject();
		final MavenPluginWrapper pluginWrapper =
		        this.getMavenPlugin(monitor, mavenProjectFacade);
		final IProject project = mavenProjectFacade.getProject();

		if (LOG.isDebugEnabled()) {
			switch (mavenProjectChangedEvent.getKind()) {
				case MavenProjectChangedEvent.KIND_ADDED:
					LOG.debug("mavenProjectChanged {}: KIND_ADDED", project);
					break;
				case MavenProjectChangedEvent.KIND_CHANGED:
					LOG.debug("mavenProjectChanged {}: KIND_CHANGED", project);
					break;
				case MavenProjectChangedEvent.KIND_REMOVED:
					LOG.debug("mavenProjectChanged {}: KIND_REMOVED", project);
					break;
				default:
					LOG.debug("mavenProjectChanged {}: {}", project,
					        mavenProjectChangedEvent.getKind());
			}
		}

		if (this.checkUnconfigurationRequired(monitor, mavenProjectFacade,
		        mavenProjectChangedEvent.getOldMavenProject())) {
			this.unconfigureEclipsePlugin(project, monitor);
			return;
		}
		if (pluginWrapper.isPluginConfigured()) {
			@SuppressWarnings("deprecation")
			final MavenExecutionRequest request =
			        maven.createExecutionRequest(monitor);
			@SuppressWarnings("deprecation")
			final MavenSession session =
			        maven.createSession(request, mavenProjectChangedEvent
			                .getMavenProject().getMavenProject(monitor));
			this.handleProjectConfigurationChange(mavenProjectFacade, project,
			        monitor, pluginWrapper, session);
		} else {
			// TODO: redirect to eclipse logger.
			// this.console.logMessage(String.format(
			// "Will not configure the Eclipse Plugin for Maven Plugin [%s:%s],"
			// +
			// "(Could not find maven plugin instance or configuration in pom)",
			// this.getMavenPluginGroupId(),
			// this.getMavenPluginArtifactId()));
		}
	}

	protected abstract void handleProjectConfigurationChange(
	        final IMavenProjectFacade mavenProjectFacade,
	        final IProject project, final IProgressMonitor monitor,
	        final MavenPluginWrapper mavenPluginWrapper, MavenSession session)
	        throws CoreException;

	/**
	 * Get the maven plugin {@code groupId}.
	 * 
	 * @return the {@code artifactId}.
	 */
	protected abstract String getMavenPluginGroupId();

	/**
	 * Get the maven plugin {@code artifactId}.
	 * 
	 * @return the {@code groupId}.
	 */
	protected abstract String getMavenPluginArtifactId();

	/**
	 * @return the specific goals that this class works on, or null if it all
	 *         goals apply. Null may lead to chaotic overlaying of multiple
	 *         configurations. If more than one, this will process in order
	 *         looking for an execution.
	 */
	protected abstract String[] getMavenPluginGoals();

	/**
	 * Unconfigure the associated Eclipse plugin.
	 * 
	 * @param project
	 *            the {@link IProject} instance.
	 * @param monitor
	 *            the {@link IProgressMonitor} instance.
	 * @throws CoreException
	 *             if unconfiguring the eclipse plugin fails.
	 */
	protected abstract void unconfigureEclipsePlugin(final IProject project,
	        final IProgressMonitor monitor) throws CoreException;

	/**
	 * Helper to check if a Eclipse plugin unconfiguration is needed. This
	 * usually happens if the maven plugin has been unconfigured.
	 * 
	 * @param curMavenProjectFacade
	 *            the current {@code IMavenProjectFacade}.
	 * @param oldMavenProjectFacade
	 *            the previous {@code IMavenProjectFacade}.
	 * @return {@code true} if the Eclipse plugin configuration needs to be
	 *         deleted.
	 * @throws CoreException
	 */
	private boolean checkUnconfigurationRequired(final IProgressMonitor monitor,
	        final IMavenProjectFacade curMavenProjectFacade,
	        final IMavenProjectFacade oldMavenProjectFacade)
	        throws CoreException {
		Preconditions.checkNotNull(curMavenProjectFacade);

		if (oldMavenProjectFacade == null) {
			return false;
		}
		final MavenPluginWrapper newMavenPlugin =
		        this.getMavenPlugin(monitor, curMavenProjectFacade);
		final MavenPluginWrapper oldMavenPlugin =
		        this.getMavenPlugin(monitor, oldMavenProjectFacade);
		if (!newMavenPlugin.isPluginConfigured()
		        && oldMavenPlugin.isPluginConfigured()) {
			return true;
		}
		return false;
	}

	public static ResourceResolver getResourceResolver(
	        final MojoExecution mojoExecution, final MavenSession session,
	        final IPath projectLocation) throws CoreException {
		// call for side effect of ensuring that the realm is set in the
		// descriptor.
		final IMaven mvn = MavenPlugin.getMaven();
		final List<IPath> pluginDepencyProjectLocations = new ArrayList<>();
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IMavenProjectRegistry mavenProjectRegistry =
		        MavenPlugin.getMavenProjectRegistry();
		final IMavenProjectFacade[] projects =
		        mavenProjectRegistry.getProjects();
		final List<Dependency> dependencies =
		        mojoExecution.getPlugin().getDependencies();
		for (final Dependency dependency : dependencies) {
			for (final IMavenProjectFacade projectFacade : projects) {
				final IProject project = projectFacade.getProject();
				if (!project.isAccessible()) {
					LOG.debug("Project registry contains closed project {}",
					        project);
					// this is actually a bug somewhere in registry refresh
					// logic, closed projects should not be there
					continue;
				}
				final ArtifactKey artifactKey = projectFacade.getArtifactKey();
				if (artifactKey.getGroupId().equals(dependency.getGroupId())
				        && artifactKey.getArtifactId()
				                .equals(dependency.getArtifactId())
				        && artifactKey.getVersion()
				                .equals(dependency.getVersion())) {
					final IResource outputLocation =
					        root.findMember(projectFacade.getOutputLocation());
					if (outputLocation != null) {
						pluginDepencyProjectLocations.add(outputLocation.getLocation());
					}
				}
			}
		}
		try {
			final Mojo configuredMojo =
			        mvn.getConfiguredMojo(session, mojoExecution, Mojo.class);
			mvn.releaseMojo(configuredMojo, mojoExecution);
		} catch (CoreException e) {
			if (pluginDepencyProjectLocations.isEmpty()) {
				throw e;
			}
			LOG.trace("Could not get mojo", e);
		}
		return new ResourceResolver(mojoExecution.getMojoDescriptor()
		        .getPluginDescriptor().getClassRealm(), projectLocation,
		        pluginDepencyProjectLocations);
	}

	private MavenPluginWrapper getMavenPlugin(final IProgressMonitor monitor,
	        final IMavenProjectFacade projectFacade) throws CoreException {
		return MavenPluginWrapper.newInstance(monitor, getMavenPluginGroupId(),
		        getMavenPluginArtifactId(), getMavenPluginGoals(),
		        projectFacade);
	}

}
