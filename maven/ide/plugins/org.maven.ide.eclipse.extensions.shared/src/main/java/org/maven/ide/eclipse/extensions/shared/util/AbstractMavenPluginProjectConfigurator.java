package org.maven.ide.eclipse.extensions.shared.util;

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
        final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(mavenProject);
        final IProject project = request.getProject();

        if (!pluginWrapper.isPluginConfigured()) {
            this.console.logMessage(String.format(
                "No Configuration found for Maven Plugin [%s:%s]: "
                + " will SKIP configuring the corresponding Eclipse Plugin nature and builder.", 
                this.getMavenPluginGroupId(),
                this.getMavenPluginArtifactId()));
            return;
        }
        
        final MavenPluginConfigurationExtractor mavenPluginCfg = 
            MavenPluginConfigurationExtractor.newInstance(pluginWrapper);
        
        if (mavenPluginCfg.shouldDisableConfigurator()) {
            this.console.logMessage(String.format(
                    "[%s]: Maven Plugin configuration indicated SKIPPING the m2e project configurator", 
                    this.getLogPrefix()));
            return;
        }
        
        this.handleProjectConfigurationChange(
                mavenProject,
                project, 
                monitor,
                pluginWrapper,
                mavenPluginCfg);
    }
    
    @Override
    protected void mavenProjectChanged(
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
            final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(mavenProject);
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
                final MavenPluginConfigurationExtractor mavenPluginCfg = 
                    MavenPluginConfigurationExtractor.newInstance(pluginWrapper);
                
                if (mavenPluginCfg.shouldDisableConfigurator()) {
                    this.console.logMessage(String.format(
                       "[%s]: Maven Plugin configuration indicated SKIPPING the m2e project configurator", 
                       this.getLogPrefix()));
                    return;
                }
                //only call handler if maven plugin is configured or found.
                this.handleProjectConfigurationChange(
                        mavenProject,
                        project, 
                        monitor,
                        pluginWrapper,
                        mavenPluginCfg);
            } else {
                this.console.logMessage(String.format(
                        "Will not configure the Eclipse Plugin for Maven Plugin [%s:%s],"
                        + "(Could not find maven plugin instance or configuration in pom)", 
                        this.getMavenPluginGroupId(),
                        this.getMavenPluginArtifactId()));
            }
        }
        super.mavenProjectChanged(mavenProjectChangedEvent, monitor);
    }

    protected abstract void handleProjectConfigurationChange(
            final MavenProject mavenProject, 
            final IProject project,
            final IProgressMonitor monitor,
            final MavenPluginWrapper mavenPluginWrapper,
            final MavenPluginConfigurationExtractor mavenPluginCfg) throws CoreException;

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
     */
    private boolean checkUnconfigurationRequired(
            final IMavenProjectFacade curMavenProjectFacade,
            final IMavenProjectFacade oldMavenProjectFacade) {
        Preconditions.checkNotNull(curMavenProjectFacade);
        
        if (oldMavenProjectFacade == null) {
            return false;
        }
        final MavenProject curMavenProject = curMavenProjectFacade.getMavenProject();
        final MavenProject oldMavenProject = oldMavenProjectFacade.getMavenProject();
        if (curMavenProject == null || oldMavenProject == null) {
            return false;
        }
        final MavenPluginWrapper newMavenPlugin = this.getMavenPlugin(
                curMavenProject);
        final MavenPluginWrapper oldMavenPlugin = this.getMavenPlugin(
                oldMavenProject);
        if (!newMavenPlugin.isPluginConfigured() && oldMavenPlugin.isPluginConfigured()) {
            return true;
        }
        return false;
    }
    
    private MavenPluginWrapper getMavenPlugin(final MavenProject mavenProject) {
        return MavenPluginWrapper.newInstance(
                this.getMavenPluginGroupId(),
                this.getMavenPluginArtifactId(), 
                mavenProject);
    }

}
