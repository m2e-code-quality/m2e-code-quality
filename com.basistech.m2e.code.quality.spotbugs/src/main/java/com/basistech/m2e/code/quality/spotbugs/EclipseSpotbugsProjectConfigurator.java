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
package com.basistech.m2e.code.quality.spotbugs;

import static com.basistech.m2e.code.quality.spotbugs.SpotbugsEclipseConstants.ECLIPSE_FB_NATURE_ID;
import static com.basistech.m2e.code.quality.spotbugs.SpotbugsEclipseConstants.ECLIPSE_FB_PREFS_FILE;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.marker.FindBugsMarker;
import de.tobject.findbugs.nature.FindBugsNature;
import edu.umd.cs.findbugs.config.UserPreferences;

public class EclipseSpotbugsProjectConfigurator
        extends AbstractMavenPluginProjectConfigurator<FindBugsNature> {

	private static final Logger LOG =
	        LoggerFactory.getLogger(EclipseSpotbugsProjectConfigurator.class);

	public EclipseSpotbugsProjectConfigurator() {
		super(ECLIPSE_FB_NATURE_ID, FindBugsMarker.NAME, ECLIPSE_FB_PREFS_FILE);
	}

	@Override
	protected String getMavenPluginArtifactId() {
		return "spotbugs-maven-plugin";
	}

	@Override
	protected String getMavenPluginGroupId() {
		return "com.github.spotbugs";
	}

	@Override
	protected String[] getMavenPluginGoals() {
		return new String[] { "spotbugs", "check" };
	}

	@Override
	protected void handleProjectConfigurationChange(
	        final IMavenProjectFacade mavenProjectFacade,
	        final IProject project, final MavenPluginWrapper mavenPluginWrapper,
	        final MavenSession session, final IProgressMonitor monitor) throws CoreException {
		LOG.debug("entering handleProjectConfigurationChange");
		final IJavaProject javaProject = JavaCore.create(project);
		if (javaProject == null || !javaProject.exists()
		        || !javaProject.getProject().isOpen()) {
			return;
		}
		final MavenPluginConfigurationTranslator mavenSpotbugsConfig =
		        MavenPluginConfigurationTranslator.newInstance(maven,
		                mavenPluginWrapper, session,
		                mavenProjectFacade.getMavenProject(monitor),
		                project, monitor);
		UserPreferences prefs;
		try {
			final List<MojoExecution> mojoExecutions =
			        mavenPluginWrapper.getMojoExecutions();
			if (mojoExecutions.size() != 1) {
				LOG.error("Wrong number of executions. Expected 1. Found "
				        + mojoExecutions.size());
				return;
			}
			prefs = this.buildSpotbugsPreferences(mavenSpotbugsConfig);
			configure(project, mavenSpotbugsConfig.isSkip(), monitor);
			UserPreferences oldPrefs = FindbugsPlugin.isProjectSettingsEnabled(project)
					? FindbugsPlugin.getUserPreferences(project)
					: null;
			if (oldPrefs == null || !oldPrefs.equals(prefs)) {
				FindbugsPlugin.saveUserPreferences(project, prefs);
				FindbugsPlugin.setProjectSettingsEnabled(project, null, true);
			}
		} catch (final CoreException ex) {
			LOG.error(ex.getLocalizedMessage(), ex);
		}
	}

	private UserPreferences buildSpotbugsPreferences(
	        final MavenPluginConfigurationTranslator pluginCfgTranslator)
	        throws CoreException {
		LOG.debug("entering buildSpotbugsPreferences");
		final UserPreferences prefs =
		        UserPreferences.createDefaultUserPreferences();
		pluginCfgTranslator.setIncludeFilterFiles(prefs);
		pluginCfgTranslator.setExcludeFilterFiles(prefs);
		// pluginCfgTranslator.setBugCatagories(prefs);
		pluginCfgTranslator.setEffort(prefs);
		pluginCfgTranslator.setMinRank(prefs);
		pluginCfgTranslator.setVisitors(prefs);
		pluginCfgTranslator.setOmitVisitors(prefs);
		pluginCfgTranslator.setPriority(prefs);
		pluginCfgTranslator.setThreshold(prefs);
		prefs.setRunAtFullBuild(false);

		FindbugsPlugin.DEBUG = pluginCfgTranslator.debugEnabled();
		return prefs;
	}

}
