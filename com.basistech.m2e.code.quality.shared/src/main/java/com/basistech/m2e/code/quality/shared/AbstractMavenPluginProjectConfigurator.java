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

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
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
public abstract class AbstractMavenPluginProjectConfigurator<N extends IProjectNature>
        extends AbstractProjectConfigurator {

	private static final Logger LOG = LoggerFactory
	        .getLogger(AbstractMavenPluginProjectConfigurator.class);

	private final String natureId;
	
	private final String markerId;

	private final String[] associatedFileNames;

	@SuppressWarnings("hiding")
	protected AbstractMavenPluginProjectConfigurator(final String natureId, final String markerId,
			final String... associatedFileNames) {
		this.natureId = natureId;
		this.markerId = markerId;
		this.associatedFileNames = associatedFileNames;
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
		LOG.debug("configure {}", request.mavenProject().getArtifact().getArtifactId());

		final MavenProject mavenProject = request.mavenProject();
		if (mavenProject == null) {
			return;
		}

		final MavenPluginWrapper pluginWrapper =
		        this.getMavenPlugin(monitor, request.mavenProjectFacade());
		final IProject project = request.mavenProjectFacade().getProject();

		if (!pluginWrapper.isPluginConfigured()) {
			return;
		}

		this.handleProjectConfigurationChange(request.mavenProjectFacade(),
		        project, pluginWrapper, monitor);
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

		if (this.checkUnconfigurationRequired(monitor, mavenProjectFacade)) {
			this.unconfigureEclipsePlugin(project, monitor);
			return;
		}
		if (pluginWrapper.isPluginConfigured()) {
			this.handleProjectConfigurationChange(mavenProjectFacade, project,
			        pluginWrapper, monitor);
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

	/**
	 * Should call {@link #configure(IProject, boolean, IProgressMonitor)} to (de-)activate nature and builder
	 */
	protected abstract void handleProjectConfigurationChange(
	        final IMavenProjectFacade mavenProjectFacade,
	        final IProject project, final MavenPluginWrapper mavenPluginWrapper,
	        final IProgressMonitor monitor)
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
	 *            the {@link IProject} to unconfigure.
	 * @param monitor
	 *            the {@link IProgressMonitor} instance.
	 * @throws CoreException
	 *             if unconfiguring the eclipse plugin fails.
	 */
	protected void unconfigureEclipsePlugin(final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		LOG.debug("entering deconfigure");
		// this removes the builder and nature
		removeNature(project, monitor);
		// remove all eclipse files.
		deleteEclipseFiles(project, monitor);
	}

	/**
	 * Helper to check if a Eclipse plugin unconfiguration is needed. This
	 * usually happens if the maven plugin has been unconfigured.
	 * 
	 * @param curMavenProjectFacade
	 *            the current {@code IMavenProjectFacade}.
	 * @return {@code true} if the Eclipse plugin configuration needs to be
	 *         deleted.
	 * @throws CoreException
	 */
	private boolean checkUnconfigurationRequired(final IProgressMonitor monitor,
	        final IMavenProjectFacade curMavenProjectFacade)
	        throws CoreException {
		Preconditions.checkNotNull(curMavenProjectFacade);

		final MavenPluginWrapper newMavenPlugin =
		        this.getMavenPlugin(monitor, curMavenProjectFacade);
		if (!newMavenPlugin.isPluginConfigured()) {
			return true;
		}
		return false;
	}

	private MavenPluginWrapper getMavenPlugin(final IProgressMonitor monitor,
	        final IMavenProjectFacade projectFacade) throws CoreException {
		return MavenPluginWrapper.newInstance(monitor, getMavenPluginGroupId(),
		        getMavenPluginArtifactId(), getMavenPluginGoals(),
		        projectFacade);
	}

	protected void configure(final IProject project, final boolean skip, final IProgressMonitor monitor) throws CoreException {
		LOG.debug("entering configure");
		if (!skip) {
			addNature(project, monitor);
		} else {
			removeNature(project, monitor);
		}
	}

	/**
	 * Get the currently configured nature in the project
	 * @return <code>null</code> if the nature is not active.
	 */
	@SuppressWarnings("unchecked")
	protected N getNature(final IProject project) throws CoreException {
		return (N) project.getNature(natureId);
	}

	@SuppressWarnings("unchecked")
	protected N addNature(final IProject project, final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering configureNature");
		// We have to explicitly add the nature.
		final IProjectDescription desc = project.getDescription();
		final String[] natures = desc.getNatureIds();
		for (int i = 0; i < natures.length; i++) {
			if (natureId.equals(natures[i])) {
				// already configured
				return (N) project.getNature(natureId);
			}
		}
		final String[] newNatures = Arrays.copyOf(natures, natures.length + 1);
		newNatures[natures.length] = natureId;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);

		// should be available now
		return (N) project.getNature(natureId);
	}

	protected void removeNature(final IProject project, final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering deconfigureNature");

		// clean all markers
		project.deleteMarkers(markerId, true, IResource.DEPTH_INFINITE);

		// remove the nature itself, by resetting the nature list.
		final IProjectDescription desc = project.getDescription();
		final String[] natures = desc.getNatureIds();
		final List<String> newNaturesList = new ArrayList<>();
		for (int i = 0; i < natures.length; i++) {
			if (!natureId.equals(natures[i])) {
				newNaturesList.add(natures[i]);
			}
		}
		if (newNaturesList.size() == natures.length) {
			// no changes
			return;
		}

		final String[] newNatures =
		        newNaturesList.toArray(new String[newNaturesList.size()]);
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);
	}

	protected void deleteEclipseFiles(final IProject project, final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering deleteEclipseFiles");
		for (String associatedFileName : associatedFileNames) {
			final IResource associatedFile = project.getFile(associatedFileName);
			if (associatedFile.exists()) {
				associatedFile.delete(IResource.FORCE, monitor);
			}
		}
	}
}
