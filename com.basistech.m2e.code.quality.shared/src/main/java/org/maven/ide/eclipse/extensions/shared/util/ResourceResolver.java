package org.maven.ide.eclipse.extensions.shared.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.eclipse.core.runtime.CoreException;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.embedder.IMaven;

/**
 * A utility class to resolve resources, which includes searching in resources
 * specified with the {@literal <dependencies>} of the Maven pluginWrapper
 * configuration.
 * 
 * @since 0.9.8
 */
public final class ResourceResolver {

    private final MavenConsole console = MavenPlugin.getDefault().getConsole();
    private final IMaven maven;
    private final MavenPluginWrapper pluginWrapper;
    private final String prefix;
    private final ClassLoader pluginClassLoader;
    
    private ResourceResolver(final MavenPluginWrapper pluginWrapper, final String prefix) {
        this.maven = MavenPlugin.getDefault().getMaven();
        this.pluginWrapper = pluginWrapper;
        this.prefix = prefix;
        this.pluginClassLoader = this.buildPluginClassLoader();
    }

    protected ClassLoader getPluginClassLoader() {
        return this.pluginClassLoader;
    }

    /**
     * Resolve the resource location as per the maven pluginWrapper spec.
     * <ol>
     *  <li>As a resource.</li>
     *  <li>As a URL.</li>
     *  <li>As a filesystem resource.</li>
     * </ol>
     * 
     * @param location the resource location as a string.
     * @return the {@code URL} of the resolved location or {@code null}.
     */
    public URL resolveLocation(final String location) {
        URL url = null;
        //1. Try it as a resource first.
        url = this.pluginClassLoader.getResource(location);
        if (url == null) {
          try {
              //2. Try it as a remote resource.
              url = new URL(location);
              //check if valid.
              url.openStream();
          } catch (MalformedURLException ex) {
              //ignored, try next
          } catch (Exception ex) {
              //ignored, try next
          }
          if (url == null) {
              //3. Try to see if it exists as a filesystem resource.
              final File f = new File(location);
              if (f.exists()) {
                  try {
                      url = f.toURL();
                  } catch (MalformedURLException ex) {
                      //ignored, try next
                  }
              }
          }
        }

        if (url == null) {
            this.console.logError(String.format(
               "[%s]: Failed to resolve location [%s]", this.prefix, location));
        } else {
            this.console.logMessage(String.format(
               "[%s]: RESOLVED location <%s> as [%s]", this.prefix, location, url));
        }
        return url;
        
    }
    
    /**
     * Get the pluginWrapper classloader with its dependencies if any. NOTE: this
     * <em>may</em> be different from the Project Classloader.
     */
    private ClassLoader buildPluginClassLoader() {
        final List<URL> jars = new LinkedList<URL>();
          //Add all pluginWrapper Dependencies
        List<Dependency> dependencies = this.pluginWrapper
            .getDependenciesIncludingSelf();
        if (dependencies != null && dependencies.size() > 0) {
            for (Dependency d : dependencies) {
                // Dependency dependency = (Dependency) dependencies.get(i);
                final Artifact a = this.getResolvedArtifact(
                        d.getGroupId(), 
                        d.getArtifactId(), 
                        d.getVersion(), 
                        d.getType(), 
                        d.getClassifier());
                if (a != null) {
                    this.addArtifactUrl(jars, a);
                }
            }
        }
        return new URLClassLoader(
                jars.toArray(new URL[jars.size()]), 
                Thread.currentThread().getContextClassLoader());
    }

    private void addArtifactUrl(final List<URL> jarList, final Artifact a) {
        try {
            jarList.add(a.getFile().toURI().toURL());
        } catch (MalformedURLException e) {
            this.console.logError(String.format(
                    "[%s]: Could not create a URL for artifact [%s]", 
                    this.prefix, a));
        }
    }
    
    private Artifact getResolvedArtifact(
            final String groupId,
            final String artifactId,
            final String version,
            final String type,
            final String classifier) {
        Artifact a = null;
        try {
            a = this.maven.resolve(
                    groupId, 
                    artifactId, 
                    version, 
                    type, 
                    classifier, 
                    null, 
                    null);
            if (a.isResolved()) {
                this.console.logMessage(String.format(
                        "[%s]: Successfully resolved artifact [%s]", 
                        this.prefix, a));
            }
        } catch (CoreException ex) {
            this.console.logError(String.format(
                    "[%s]: Resolution for artifact [%s:%s:%s] failed.", 
                    this.prefix,
                    groupId,
                    artifactId,
                    version));
        }
        return a;
    }
    
    /**
     * Factory to create a new instance of {@link ResourceResolver}.
     * 
     * @param pluginWrapper The {@code MavenPluginWrapper} instance.
     * @param prefix        A prefix string for console log messages.
     * @return              new instance of {@link ResourceResolver}.
     */
    public static ResourceResolver newInstance(
            final MavenPluginWrapper pluginWrapper, 
            final String prefix) {
        return new ResourceResolver(pluginWrapper, prefix);
    }
}
