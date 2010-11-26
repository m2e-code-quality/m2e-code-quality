package org.maven.ide.eclipse.extensions.shared.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecution;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.project.IMavenProjectFacade;

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
    
    private static MojoExecution findMavenPlugin(
    		IProgressMonitor monitor,
            IMavenProjectFacade mavenProjectFacade,
            final String pluginGroupId,
            final String pluginArtifactId,
            final String pluginGoal) throws CoreException {
    	MavenExecutionPlan executionPlan = mavenProjectFacade.getExecutionPlan(monitor);
    	List<MojoExecution> mojoExecutions = executionPlan.getMojoExecutions();
    	for (MojoExecution mojoExecution : mojoExecutions) {
    		if(mojoExecutionForPlugin(mojoExecution, pluginGroupId, pluginArtifactId, pluginGoal)) {
    			return mojoExecution;
    		}
    	}
        return null;
    }
    
    public static MavenPluginWrapper newInstance(
    		IProgressMonitor monitor,
            final String pluginGroupId,
            final String pluginArtifactId,
            final String pluginGoal,
            IMavenProjectFacade mavenProjectFacade) throws CoreException {
        Preconditions.checkNotNull(mavenProjectFacade);
        final MojoExecution execution = findMavenPlugin(monitor,
                mavenProjectFacade, pluginGroupId, pluginArtifactId, pluginGoal);
        String key = pluginGroupId + ":" + pluginArtifactId;
        if (pluginGoal != null) {
        	key = key + ":" + pluginGoal;
        }
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
