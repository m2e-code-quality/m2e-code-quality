package org.maven.ide.eclipse.extensions.shared.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;

/**
 * A utility class to resolve resources, which includes searching in resources
 * specified with the {@literal <dependencies>} of the Maven pluginWrapper
 * configuration.
 * 
 * @since 0.9.8
 */
public final class ResourceResolver {

    private final MavenConsole console = MavenPlugin.getDefault().getConsole();
    private final String prefix;
    private ClassRealm pluginRealm;
    
    private ResourceResolver(ClassRealm pluginRealm, final String prefix) {
    	this.pluginRealm = pluginRealm;
        this.prefix = prefix;
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
        url = pluginRealm.getResource(location);
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
                      url = f.toURI().toURL();
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
     * Factory to create a new instance of {@link ResourceResolver}.
     * 
     * @param pluginWrapper The {@code MavenPluginWrapper} instance.
     * @param prefix        A prefix string for console log messages.
     * @return              new instance of {@link ResourceResolver}.
     */
    public static ResourceResolver newInstance(ClassRealm pluginRealm,
    		final String prefix) {
        return new ResourceResolver(pluginRealm, prefix);
    }
}
