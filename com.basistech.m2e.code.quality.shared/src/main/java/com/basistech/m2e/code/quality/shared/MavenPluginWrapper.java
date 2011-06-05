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
import java.util.Collections;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;


public class MavenPluginWrapper {
	private String key; // for toString
	private final MojoExecution execution;
    
    private MavenPluginWrapper(final String key, final MojoExecution execution) {
    	this.key = key;
    	this.execution = execution;
    }

    public boolean isPluginConfigured() {
        return this.execution == null ? false : true;
    }
    
    public MojoExecution getMojoExecution() {
    	return execution;
    }
    

    /**
     * Return a list of dependencies of the plugin as configured in the
     * {@code <dependencies>} element of the plugin configuration <em>including</em>
     * the plugin itself, which will the always be the first element of the
     * returned list of dependencies.
     * 
     * <p>
     * This is mainly to build an isolated classpath for the plugin.
     * </p>
     * @return a {@code List} of the plugin dependencies, if the plugin was actually
     *         configured in the project interpolated {@code <build>} elements, else
     *         returns an {@code empty list}.
     */
    public List<Dependency> getDependenciesIncludingSelf() {
        if (this.execution == null) {
            return Collections.emptyList();
        }
        List<Dependency> dl = new ArrayList<Dependency>();
        dl.add(this.getPluginAsDependency());
        dl.addAll(execution.getPlugin().getDependencies());
        return ImmutableList.copyOf(dl);
    }

    private Dependency getPluginAsDependency() {
        final Dependency d = new Dependency();
        d.setGroupId(execution.getPlugin().getGroupId());
        d.setArtifactId(execution.getPlugin().getArtifactId());
        d.setVersion(execution.getPlugin().getVersion());
        d.setType("maven-plugin");
        d.setClassifier(null);
        return d;
    }
    
    public static boolean mojoExecutionForPlugin(MojoExecution mojoExecution, String groupId, String artifactId,
    			String goal) {
    	return groupId.equals(mojoExecution.getGroupId())
    			&& artifactId.equals(mojoExecution.getArtifactId())
    			&& (goal == null || goal.equals(mojoExecution.getGoal()));
    }
    
    public static MojoExecution findMojoExecution(
    		IProgressMonitor monitor,
            IMavenProjectFacade mavenProjectFacade,
            final String pluginGroupId,
            final String pluginArtifactId,
            final String[] pluginGoal) throws CoreException {
        List<MojoExecution> mojoExecutions = mavenProjectFacade.getMojoExecutions(pluginGroupId, pluginArtifactId, monitor, pluginGoal);
        // I don't think we need to re-search for site.
        return searchExecutions(pluginGroupId, pluginArtifactId, pluginGoal, mojoExecutions);
    }//

	private static MojoExecution searchExecutions(final String pluginGroupId,
			final String pluginArtifactId, final String[] pluginGoal,
			List<MojoExecution> mojoExecutions) {
		for (MojoExecution mojoExecution : mojoExecutions) {
    		if (pluginGoal != null) {
    			for (String goal : pluginGoal) {
    	    		if(mojoExecutionForPlugin(mojoExecution, pluginGroupId, pluginArtifactId, goal)) {
    	    			return mojoExecution;
    	    		}
    			}
    		} else {
    			if(mojoExecutionForPlugin(mojoExecution, pluginGroupId, pluginArtifactId, null)) {
	    			return mojoExecution;
	    		}
    		}
    	}
		return null;
	}
    
    public static MavenPluginWrapper newInstance(
    		IProgressMonitor monitor,
            final String pluginGroupId,
            final String pluginArtifactId,
            final String[] pluginGoal,
            IMavenProjectFacade mavenProjectFacade) throws CoreException {
        Preconditions.checkNotNull(mavenProjectFacade);
        final MojoExecution execution = findMojoExecution(monitor, mavenProjectFacade, pluginGroupId, pluginArtifactId, pluginGoal);
        String key = pluginGroupId + ":" + pluginArtifactId;
        return new MavenPluginWrapper(key, execution);
    }
    
    @Override
	public String toString() {
    	String s = "[MavenPluginWrapper " + key;
    	if (execution == null) {
    		s = s + " null wrapper]";
    	}
    	return s;
	}
}
