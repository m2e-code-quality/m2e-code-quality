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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.codehaus.plexus.classworlds.realm.ClassRealm;

/**
 * A utility class to resolve resources, which includes searching in resources
 * specified with the {@literal <dependencies>} of the Maven pluginWrapper
 * configuration.
 * 
 * @since 0.9.8
 */
public final class ResourceResolver {

    private ClassRealm pluginRealm;
    
    private ResourceResolver(ClassRealm pluginRealm) {
    	this.pluginRealm = pluginRealm;
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
        // not that class loaders don't want leading slashes.
        String urlLocation = location;
        if (urlLocation.startsWith("/")) {
        	urlLocation = urlLocation.substring(1);
        }
        url = pluginRealm.getResource(urlLocation);
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
        return url;
        
    }

    /**
     * Factory to create a new instance of {@link ResourceResolver}.
     * 
     * @param pluginWrapper The {@code MavenPluginWrapper} instance.
     * @param prefix        A prefix string for console log messages.
     * @return              new instance of {@link ResourceResolver}.
     */
    public static ResourceResolver newInstance(ClassRealm pluginRealm) {
        return new ResourceResolver(pluginRealm);
    }
}
