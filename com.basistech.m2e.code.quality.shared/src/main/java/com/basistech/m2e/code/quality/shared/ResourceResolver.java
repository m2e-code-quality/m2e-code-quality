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
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.core.runtime.IPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * A utility class to resolve resources, which includes searching in resources
 * specified with the {@literal <dependencies>} of the Maven pluginWrapper
 * configuration.
 * 
 * @since 0.9.8
 */
public final class ResourceResolver {

	private static final Logger LOG =
	        LoggerFactory.getLogger(ResourceResolver.class);

	private final ClassRealm pluginRealm;
	private final IPath projectLocation;

	public ResourceResolver(final ClassRealm pluginRealm,
	        final IPath projectLocation) {
		Preconditions.checkNotNull(pluginRealm);
		Preconditions.checkNotNull(projectLocation);
		this.pluginRealm = pluginRealm;
		this.projectLocation = projectLocation;
	}

	/**
	 * Resolve the resource location as per the maven pluginWrapper spec.
	 * <ol>
	 * <li>As a resource.</li>
	 * <li>As a URL.</li>
	 * <li>As a filesystem resource.</li>
	 * </ol>
	 * 
	 * @param location
	 *            the resource location as a string.
	 * @return the {@code URL} of the resolved location or {@code null}.
	 */
	public URL resolveLocation(final String location) {
		if (location == null || location.isEmpty()) {
			return null;
		}
		URL url = getResourceFromPluginRealm(location);
		if (url == null) {
			url = getResourceFromRemote(location);
		}
		if (url == null) {
			url = getResourceFromFileSystem(location);
		}
		if (url == null) {
			url = getResourceRelativeFromProjectLocation(location);
		}
		return url;
	}

	public URL getResourceFromPluginRealm(final String resource) {
		if (resource.startsWith("/")) {
			// ClassLoaders don't want leading slashes
			return pluginRealm.getResource(resource.substring(1));
		} else {
			return pluginRealm.getResource(resource);
		}
	}

	public URL getResourceFromRemote(final String resource) {
		try {
			return new URL(resource);
		} catch (final IOException e) {
			LOG.trace("Could not open resource {} from remote", resource, e);
		}
		return null;
	}

	public URL getResourceFromFileSystem(final String resource) {
		try {
			final Path path = Paths.get(resource);
			if (Files.exists(path)) {
				return path.toUri().toURL();
			}
		} catch (InvalidPathException | IOException e) {
			LOG.trace("Could not open resource {} from file system", resource,
			        e);
		}
		return null;
	}

	public URL getResourceRelativeFromProjectLocation(final String resource) {
		try {
			final File file = projectLocation.append(resource).toFile();
			if (file.exists()) {
				return file.toURI().toURL();
			}
		} catch (final IOException e) {
			LOG.trace("Could not open resource {} relative to project location",
			        resource, e);
		}
		return null;
	}

}
