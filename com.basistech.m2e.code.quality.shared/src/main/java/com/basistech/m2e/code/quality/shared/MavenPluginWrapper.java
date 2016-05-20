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
import java.util.List;

import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class MavenPluginWrapper {

	private static final Logger LOG =
	        LoggerFactory.getLogger(MavenPluginWrapper.class);

	private final String key; // for toString
	private final List<MojoExecution> executions;

	private MavenPluginWrapper(final String key,
	        final List<MojoExecution> executions) {
		this.key = key;
		this.executions = executions;
	}

	public boolean isPluginConfigured() {
		return this.executions != null && !this.executions.isEmpty();
	}

	public List<MojoExecution> getMojoExecutions() {
		return executions;
	}

	public static boolean mojoExecutionForPlugin(
	        final MojoExecution mojoExecution, final String groupId,
	        final String artifactId, final String goal) {
		return groupId.equals(mojoExecution.getGroupId())
		        && artifactId.equals(mojoExecution.getArtifactId())
		        && (goal == null || goal.equals(mojoExecution.getGoal()));
	}

	public static List<MojoExecution> findMojoExecutions(
	        final IProgressMonitor monitor,
	        final IMavenProjectFacade mavenProjectFacade,
	        final String pluginGroupId, final String pluginArtifactId,
	        final String[] pluginGoal) throws CoreException {
		final List<MojoExecution> mojoExecutions =
		        mavenProjectFacade.getMojoExecutions(pluginGroupId,
		                pluginArtifactId, monitor, pluginGoal);
		// I don't think we need to re-search for site.
		return searchExecutions(pluginGroupId, pluginArtifactId, pluginGoal,
		        mojoExecutions);
	}

	private static List<MojoExecution> searchExecutions(
	        final String pluginGroupId, final String pluginArtifactId,
	        final String[] pluginGoal,
	        final List<MojoExecution> mojoExecutions) {
		final List<MojoExecution> foundMojoExections = new ArrayList<>();
		for (final MojoExecution mojoExecution : mojoExecutions) {
			if (pluginGoal != null) {
				for (final String goal : pluginGoal) {
					if (mojoExecutionForPlugin(mojoExecution, pluginGroupId,
					        pluginArtifactId, goal)) {
						foundMojoExections.add(mojoExecution);
					}
				}
			} else {
				if (mojoExecutionForPlugin(mojoExecution, pluginGroupId,
				        pluginArtifactId, null)) {
					foundMojoExections.add(mojoExecution);
				}
			}
		}
		if (foundMojoExections.size() != mojoExecutions.size()) {
			LOG.debug("They are different. Additional search is necessary.");
		}
		return foundMojoExections;
	}

	public static MavenPluginWrapper newInstance(final IProgressMonitor monitor,
	        final String pluginGroupId, final String pluginArtifactId,
	        final String[] pluginGoal,
	        final IMavenProjectFacade mavenProjectFacade) throws CoreException {
		Preconditions.checkNotNull(mavenProjectFacade);
		final List<MojoExecution> executions =
		        findMojoExecutions(monitor, mavenProjectFacade, pluginGroupId,
		                pluginArtifactId, pluginGoal);
		final String key = pluginGroupId + ":" + pluginArtifactId;
		return new MavenPluginWrapper(key, executions);
	}

	@Override
	public String toString() {
		String s = "[MavenPluginWrapper " + key;
		if (executions == null) {
			s = s + " null wrapper]";
		} else if (executions.isEmpty()) {
			s = s + " empty wrapper]";
		}
		return s;
	}
}
