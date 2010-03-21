package org.maven.ide.eclipse.extensions.shared.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;


public class MavenPluginWrapper {
    
    private static final String DEF_PLUGIN_CONFIG_ELEM_NAME =
        "configuration";
    private final Plugin plugin;
    private MavenPluginWrapper(final Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isPluginConfigured() {
        return this.plugin == null ? false : true;
    }
    
    /**
     * Retrieve the &lt;configuration&gt; element for this plugin instance. Note
     * this is guaranteed to be never {@code null}.
     * 
     * @return {@code Xpp3Dom} instance.
     */
    public Xpp3Dom getPluginConfigurationDom() {
        Xpp3Dom pluginConfigDom = new Xpp3Dom(DEF_PLUGIN_CONFIG_ELEM_NAME);
        if (this.plugin != null) {
            final Xpp3Dom realPluginConfigDom = (Xpp3Dom) this.plugin.getConfiguration();
            if (realPluginConfigDom != null) {
                pluginConfigDom = realPluginConfigDom;
            }
        }
        return pluginConfigDom;
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
        List<Dependency> dl = new ArrayList<Dependency>();
        if (this.plugin == null) {
            return dl;
        }
        dl.add(this.getPluginAsDependency());
        dl.addAll(this.plugin.getDependencies());
        return ImmutableList.copyOf(dl);
    }

    private Dependency getPluginAsDependency() {
        final Dependency d = new Dependency();
        d.setGroupId(this.plugin.getGroupId());
        d.setArtifactId(this.plugin.getArtifactId());
        d.setVersion(this.plugin.getVersion());
        d.setType("maven-plugin");
        d.setClassifier(null);
        return d;
    }
    
    private static Plugin findMavenPlugin(
            final MavenProject mavenProject,
            final String pluginGroupId,
            final String pluginArtifactId) {
        for (Plugin p : mavenProject.getBuildPlugins()) {
            if (pluginGroupId.equals(p.getGroupId())
                    && pluginArtifactId.equals(p.getArtifactId())) { 
                return p; 
            }
        }
        return null;
    }
    
    public static MavenPluginWrapper newInstance(
            final String pluginGroupId,
            final String pluginArtifactId,
            final MavenProject mavenProject) {
        Preconditions.checkNotNull(mavenProject);
        final Plugin mavenPlugin = findMavenPlugin(
                mavenProject, pluginGroupId, pluginArtifactId);
        return new MavenPluginWrapper(mavenPlugin);
    }
}
