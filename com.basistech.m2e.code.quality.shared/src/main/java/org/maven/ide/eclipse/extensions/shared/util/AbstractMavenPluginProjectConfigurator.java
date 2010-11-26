package org.maven.ide.eclipse.extensions.shared.util;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.project.IMavenProjectFacade;
import org.maven.ide.eclipse.project.MavenProjectChangedEvent;
import org.maven.ide.eclipse.project.configurator.AbstractProjectConfigurator;
import org.maven.ide.eclipse.project.configurator.ProjectConfigurationRequest;

import com.google.common.base.Preconditions;

/**
 * An abstract class merging the required callbacks of {@link AbstractProjectConfigurator},
 * suitable for maven plugins that have a corresponding Eclipse Plugin, whose configuration
 * can be established by the plugin configuration.
 * 
 */
public abstract class AbstractMavenPluginProjectConfigurator
        extends AbstractProjectConfigurator {

    @Override
	public <T> T getParameterValue(String parameter, Class<T> asType,
			MavenSession session, MojoExecution mojoExecution)
			throws CoreException {
		return super.getParameterValue(parameter, asType, session, mojoExecution);
	}

	@Override
    public void configure(
            final ProjectConfigurationRequest request, 
            final IProgressMonitor monitor)
            throws CoreException {

        final MavenProject mavenProject = request.getMavenProject();
        if (mavenProject == null) {
            this.console.logError(String.format(
               "No MavenProject instance found while configuring [%s:%s], "
               + " will SKIP corresponding Eclipse Plugin Configuration",
               this.getMavenPluginGroupId(),
               this.getMavenPluginArtifactId()));
            return;
        }

        final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(request.getMavenProjectFacade());
        final IProject project = request.getProject();

        if (!pluginWrapper.isPluginConfigured()) {
            this.console.logMessage(String.format(
                "No Configuration found for Maven Plugin [%s:%s]: "
                + " will SKIP configuring the corresponding Eclipse Plugin nature and builder.", 
                this.getMavenPluginGroupId(),
                this.getMavenPluginArtifactId()));
            return;
        }
        
        this.handleProjectConfigurationChange(
        		request.getMavenSession(),
                mavenProject,
                project, 
                monitor,
                pluginWrapper);
    }
    
    @Override
    public void mavenProjectChanged(
            final MavenProjectChangedEvent mavenProjectChangedEvent, 
            final IProgressMonitor monitor)
            throws CoreException {
        final IMavenProjectFacade mavenProjectFacade = 
            mavenProjectChangedEvent.getMavenProject();
        
        if (mavenProjectFacade != null) {
            final MavenProject mavenProject = mavenProjectFacade.getMavenProject();
            if (mavenProject == null) {
                this.console.logError(String.format(
                   "No MavenProject instance found while configuring [%s:%s], "
                   + " will SKIP corresponding Eclipse Plugin Configuration",
                   this.getMavenPluginGroupId(),
                   this.getMavenPluginArtifactId()));
            }
            final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(mavenProjectFacade);
            final IProject project = mavenProjectFacade.getProject();
            if (this.checkUnconfigurationRequired(
                    mavenProjectFacade, 
                    mavenProjectChangedEvent.getOldMavenProject())) {
                this.unconfigureEclipsePlugin(project, monitor);
                this.console.logMessage(String.format(
                    "A Maven Project appears to have removed [%s:%s] plugin, "
                    + " will remove corresponding Eclipse Plugin nature and builder."
                    + "WARNING: This will remove ALL Eclipse Plugin Persistent Data.", 
                    this.getMavenPluginGroupId(),
                    this.getMavenPluginArtifactId()));
                return;
            }
            if (pluginWrapper.isPluginConfigured()) {
				//only call handler if maven plugin is configured or found.
            	// we need a session.
                MavenExecutionRequest request = maven.createExecutionRequest(monitor);
            	MavenSession session = maven.createSession(request, mavenProject);
                this.handleProjectConfigurationChange(
                		session,
                        mavenProject,
                        project, 
                        monitor,
                        pluginWrapper);
            } else {
                //TODO: redirect to eclipse logger.
//                this.console.logMessage(String.format(
//                        "Will not configure the Eclipse Plugin for Maven Plugin [%s:%s],"
//                        + "(Could not find maven plugin instance or configuration in pom)", 
//                        this.getMavenPluginGroupId(),
//                        this.getMavenPluginArtifactId()));
            }
        }
        super.mavenProjectChanged(mavenProjectChangedEvent, monitor);
    }

    protected abstract void handleProjectConfigurationChange(
    		final MavenSession session,
            final MavenProject mavenProject, 
            final IProject project,
            final IProgressMonitor monitor,
            final MavenPluginWrapper mavenPluginWrapper) throws CoreException;

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
     * Return the specific goal that this class works on, or null if it all goals apply.
     * Null may lead to chaotic overlaying of multiple configurations.
     * @return
     */
    protected String getMavenPluginGoal() {
    	return null;
    }

    /**
     * Get the log prefix to be used when emitting console log messages.
     * 
     * @return the {@code logPrefix}.
     */
    protected abstract String getLogPrefix();

    /**
     * Unconfigure the associated Eclipse plugin.
     * 
     * @param project        the {@link IProject} instance.
     * @param monitor        the {@link IProgressMonitor} instance.
     * @throws CoreException if unconfiguring the eclipse plugin fails.
     */
    protected abstract void unconfigureEclipsePlugin(
            final IProject project, 
            final IProgressMonitor monitor) throws CoreException;

    
    /**
     * Helper to check if a Eclipse plugin unconfiguration is needed. This
     * usually happens if the maven plugin has been unconfigured.
     * 
     * @param curMavenProjectFacade the current {@code IMavenProjectFacade}.
     * @param oldMavenProjectFacade the previous {@code IMavenProjectFacade}.
     * @return {@code true} if the Eclipse plugin configuration needs to be deleted.
     * @throws CoreException 
     */
    private boolean checkUnconfigurationRequired(
            final IMavenProjectFacade curMavenProjectFacade,
            final IMavenProjectFacade oldMavenProjectFacade) throws CoreException {
        Preconditions.checkNotNull(curMavenProjectFacade);
        
        if (oldMavenProjectFacade == null) {
            return false;
        }
        final MavenPluginWrapper newMavenPlugin = this.getMavenPlugin(
        		curMavenProjectFacade);
        final MavenPluginWrapper oldMavenPlugin = this.getMavenPlugin(
        		oldMavenProjectFacade);
        if (!newMavenPlugin.isPluginConfigured() && oldMavenPlugin.isPluginConfigured()) {
            return true;
        }
        return false;
    }
    
    private MavenPluginWrapper getMavenPlugin(final IMavenProjectFacade projectFacade) throws CoreException {
        return MavenPluginWrapper.newInstance(
                this.getMavenPluginGroupId(),
                this.getMavenPluginArtifactId(), 
                this.getMavenPluginGoal(),
                projectFacade);
    }

}
