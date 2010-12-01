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
package org.maven.ide.eclipse.extensions.shared.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.embedder.IMaven;
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
    
    public List<String> getCommaSeparatedStringParameterValues(String parameter,
    		MavenSession session, MojoExecution execution) throws CoreException {
    	String value = getParameterValue(parameter, String.class, session, execution);
    	if (value == null) {
    		return Collections.emptyList();
    	} else {
    		return Arrays.asList(value.split(","));
    	}
    }
    
    protected MojoExecution findForkedExecution(MojoExecution primary,
    		String groupId,
    		String artifactId,
    		String goal) {
    	 Map<String, List<MojoExecution>> forkedExecutions = primary.getForkedExecutions();
         MojoExecution goalExecution = null;
         for (List<MojoExecution> possibleExecutionList : forkedExecutions.values()) {
         	for (MojoExecution possibleExecution : possibleExecutionList) {
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

        final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(monitor,
        		request.getMavenProjectFacade());
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
                request.getMavenProjectFacade(),
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
            final MavenPluginWrapper pluginWrapper = this.getMavenPlugin(monitor, mavenProjectFacade);
            final IProject project = mavenProjectFacade.getProject();
            if (this.checkUnconfigurationRequired(
            		monitor,
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
                        mavenProjectFacade,
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
            final IMavenProjectFacade mavenProjectFacade, 
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
    		IProgressMonitor monitor,
            final IMavenProjectFacade curMavenProjectFacade,
            final IMavenProjectFacade oldMavenProjectFacade) throws CoreException {
        Preconditions.checkNotNull(curMavenProjectFacade);
        
        if (oldMavenProjectFacade == null) {
            return false;
        }
        final MavenPluginWrapper newMavenPlugin = this.getMavenPlugin(
        		monitor,
        		curMavenProjectFacade);
        final MavenPluginWrapper oldMavenPlugin = this.getMavenPlugin(
        		monitor,
        		oldMavenProjectFacade);
        if (!newMavenPlugin.isPluginConfigured() && oldMavenPlugin.isPluginConfigured()) {
            return true;
        }
        return false;
    }
    
    public ClassRealm getPluginClassRealm(MavenSession session, MojoExecution mojoExecution) throws CoreException {
    	IMaven maven = MavenPlugin.getDefault().getMaven();
    	// call for side effect of ensuring that the realm is set in the descriptor.
    	maven.getConfiguredMojo(session, mojoExecution, Mojo.class);
    	MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
    	return mojoDescriptor.getPluginDescriptor().getClassRealm();
    }
    
    private MavenPluginWrapper getMavenPlugin(IProgressMonitor monitor,
    		final IMavenProjectFacade projectFacade) throws CoreException {
        return MavenPluginWrapper.newInstance(
        		monitor,
                this.getMavenPluginGroupId(),
                this.getMavenPluginArtifactId(), 
                this.getMavenPluginGoal(),
                projectFacade);
    }

}
