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
package com.basistech.m2e.code.quality.findbugs;

import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.MAVEN_PLUGIN_ARTIFACTID;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.MAVEN_PLUGIN_GROUPID;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.preferences.FindBugsPreferenceInitializer;
import edu.umd.cs.findbugs.config.UserPreferences;

/**
 */
public class EclipseFindbugsProjectConfigurator extends
		AbstractMavenPluginProjectConfigurator {

	private static final Logger log = LoggerFactory
			.getLogger("com/basistech/m2e/code/quality/findbugs/EclipseFindbugsProjectConfigurator");

	public EclipseFindbugsProjectConfigurator() {
		super();
	}

	@Override
	protected String getMavenPluginArtifactId() {
		return MAVEN_PLUGIN_ARTIFACTID;
	}

	@Override
	protected String getMavenPluginGroupId() {
		return MAVEN_PLUGIN_GROUPID;
	}

	@Override
	protected String[] getMavenPluginGoal() {
		return new String[] { "findbugs" };
	}

	@Override
	protected void handleProjectConfigurationChange(final MavenSession session,
			final IMavenProjectFacade mavenProjectFacade,
			final IProject project, final IProgressMonitor monitor,
			final MavenPluginWrapper mavenPluginWrapper) throws CoreException {
		log.debug("entering handleProjectConfigurationChange");
		final MavenPluginConfigurationTranslator mavenFindbugsConfig = MavenPluginConfigurationTranslator
				.newInstance(this, session,
						mavenProjectFacade.getMavenProject(monitor),
						mavenPluginWrapper, project);
		UserPreferences prefs;
		try {
			prefs = this.buildFindbugsPreferences(project, mavenFindbugsConfig,
					session, mavenPluginWrapper.getMojoExecution());
			final EclipseFindbugsConfigManager fbPluginNature = EclipseFindbugsConfigManager
					.newInstance(project);
			// Add the builder and nature
			fbPluginNature.configure(monitor);
			FindbugsPlugin.saveUserPreferences(project, prefs);
			FindbugsPlugin.setProjectSettingsEnabled(project, null, true);
		} catch (final CoreException ex) {
			log.error(ex.getLocalizedMessage());
		}
	}

	@Override
	protected void unconfigureEclipsePlugin(final IProject project,
			final IProgressMonitor monitor) throws CoreException {
		log.debug("entering unconfigureEclipsePlugin");
		final EclipseFindbugsConfigManager fbPluginNature = EclipseFindbugsConfigManager
				.newInstance(project);
		fbPluginNature.deconfigure(monitor);

	}

	private UserPreferences buildFindbugsPreferences(final IProject project,
			final MavenPluginConfigurationTranslator pluginCfgTranslator,
			final MavenSession session, final MojoExecution execution)
			throws CoreException {
		log.debug("entering buildFindbugsPreferences");
		final UserPreferences prefs = FindBugsPreferenceInitializer
				.createDefaultUserPreferences();
		pluginCfgTranslator.setIncludeFilterFiles(prefs);
		pluginCfgTranslator.setExcludeFilterFiles(prefs);
		//pluginCfgTranslator.setBugCatagories(prefs);
		pluginCfgTranslator.setEffort(prefs);
		pluginCfgTranslator.setMinRank(prefs);
		pluginCfgTranslator.setVisitors(prefs);
		pluginCfgTranslator.setOmitVisitors(prefs);
		pluginCfgTranslator.setPriority(prefs);
		pluginCfgTranslator.setThreshold(prefs);
		
		FindbugsPlugin.DEBUG = pluginCfgTranslator.debugEnabled();
		return prefs;
	}

}
