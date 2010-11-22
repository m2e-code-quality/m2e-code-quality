package org.maven.ide.eclipse.extensions.project.configurators.findbugs;

import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.FB_PREFS_FILE;
import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.LOG_PREFIX;
import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.MAVEN_PLUGIN_ARTIFACTID;
import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.MAVEN_PLUGIN_GROUPID;

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.extensions.shared.util.AbstractMavenPluginProjectConfigurator;
import org.maven.ide.eclipse.extensions.shared.util.ConfigurationException;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.nature.FindBugsNature;
import de.tobject.findbugs.preferences.FindBugsPreferenceInitializer;
import edu.umd.cs.findbugs.config.UserPreferences;
/**
 * Configures the Eclipse Findbugs Plugin when a Maven Project with findbugs-maven-plugin
 * enabled is imported or added to the Maven Project.
 * 
 */
public class EclipseFindbugsProjectConfigurator
        extends AbstractMavenPluginProjectConfigurator {

    @Override
    protected String getMavenPluginGroupId() {
        return MAVEN_PLUGIN_GROUPID;
    }

    @Override
    protected String getMavenPluginArtifactId() {
        return MAVEN_PLUGIN_ARTIFACTID;
    }

    @Override
    protected String getLogPrefix() {
        return LOG_PREFIX;
    }
    
    @Override
    protected void unconfigureEclipsePlugin(final IProject project, final IProgressMonitor monitor) 
        throws CoreException {

        this.removeFindbugsNature(project, monitor);

        //delete .fbprefs if any
        this.console.logMessage(String.format(
                "[%s]: Attempting to remove [%s] from project", 
                this.getLogPrefix(), FB_PREFS_FILE));
        final IResource fbPrefs = project.getFile(FB_PREFS_FILE);
        fbPrefs.delete(IResource.FORCE, monitor);
    }
    

    @Override
    protected void handleProjectConfigurationChange(
            final MavenProject mavenProject,
            final IProject project,
            final IProgressMonitor monitor,
            final MavenPluginWrapper pluginWrapper,
            final MavenPluginConfigurationExtractor mavenPluginCfg)
            throws CoreException {

        this.console.logMessage(String.format(
                "[%s]: Eclipse FINDBUGS Configuration STARTED", LOG_PREFIX));
        
        final MavenPluginConfigurationTranslator pluginCfgTranslator = 
            MavenPluginConfigurationTranslator.newInstance(
                    mavenProject,
                    pluginWrapper,
                    project);
        UserPreferences prefs;
        try {
            prefs = this.buildFindbugsPreferences(project, pluginCfgTranslator);
        } catch (final ConfigurationException ex) {
            this.console.logMessage(String.format(
                    "[%s]: Eclipse FINDBUGS Configuration ABORTED", LOG_PREFIX));
            return;
        }
        FindbugsPlugin.saveUserPreferences(project, prefs);

        this.addFindbugsNature(project, monitor);
        this.console.logMessage(String.format(
                "[%s]: Eclipse FINDBUGS Configuration ENDED", LOG_PREFIX));
    }

    /**
     * Add FindBugs nature and builder to the Eclipse project.
     * 
     * @param project The Eclipse {@code IProject} instance.
     * @param monitor Eclipse {@code IProgressMonitor} instance. {@literal unused}.
     */
    private void addFindbugsNature(final IProject project, final IProgressMonitor monitor) 
        throws CoreException {
        final FindBugsNature fbNature = new FindBugsNature();
        fbNature.setProject(project);
        fbNature.configure();
        this.console.logMessage(String.format(
                "[%s]: ADDED FINDBUGS nature and builder to the eclipse project",
                LOG_PREFIX));
    }

    /**
     * Remove FindBugs nature and builder to the Eclipse project.
     * 
     * @param project The Eclipse {@code IProject} instance.
     * @param monitor Eclipse {@code IProgressMonitor} instance. {@literal unused}.
     */
    private void removeFindbugsNature(final IProject project, final IProgressMonitor monitor) 
        throws CoreException {
        final FindBugsNature fbNature = new FindBugsNature();
        fbNature.setProject(project);
        fbNature.deconfigure();
        this.console.logMessage(String.format(
                "[%s]: REMOVED FINDBUGS nature and builder to the eclipse project",
                LOG_PREFIX));
    }
    
    /**
     * Build the FindBugs Eclipse plugin preferences object.
     * 
     * <p>
     * <ul>
     *  <li>The preferences file is not reused from the current eclipse project/workspace
     *      configuration.
     *  </li>
     * </ul>
     * </p>
     * 
     * @param project the eclipse {@code IProject} instance.
     * @param pluginCfgTranslator the maven plugin configuration translator object.
     * @return
     */
    private UserPreferences buildFindbugsPreferences(
            final IProject project,
            final MavenPluginConfigurationTranslator pluginCfgTranslator) {
        //final UserPreferences prefs = FindbugsPlugin.getUserPreferences(project);        
        //Always create a new one
        final UserPreferences prefs = FindBugsPreferenceInitializer.createDefaultUserPreferences();        
        pluginCfgTranslator.setEffort(prefs);
        pluginCfgTranslator.setIncludeFilterFiles(prefs);
        pluginCfgTranslator.setExcludeFilterFiles(prefs);
        pluginCfgTranslator.setThreshold(prefs);
        pluginCfgTranslator.setVisitors(prefs);
        //omit *must* come after above, see impl.
        pluginCfgTranslator.setOmitVisitors(prefs);
        pluginCfgTranslator.setBugCatagories(prefs);
        FindbugsPlugin.DEBUG = pluginCfgTranslator.debugEnabled();
        return prefs;
    }
}
