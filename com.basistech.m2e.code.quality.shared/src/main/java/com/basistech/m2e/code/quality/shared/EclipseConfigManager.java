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
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper on a nature that manages an associated (preferences) file, too.
 *
 */
public class EclipseConfigManager {

	private static final Logger LOG =
	        LoggerFactory.getLogger(EclipseConfigManager.class);

	private final IProjectNature nature;

	private final String natureId;

	private final String associatedFileName;

	protected EclipseConfigManager(final IProjectNature nature, final String natureId,
	        final String associatedFileName) {
		this.nature = nature;
		this.natureId = natureId;
		this.associatedFileName = associatedFileName;
	}

	public void configure(final IProgressMonitor monitor) throws CoreException {
		LOG.debug("entering configure");
		// this adds the builder only.
		this.nature.configure();
		this.configureNature(monitor);
	}

	public void deconfigure(final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering deconfigure");
		// this removes the builder only.
		this.nature.deconfigure();
		this.deconfigureNature(monitor);
		// remove all eclipse checkstyle files.
		this.deleteEclipseFiles(monitor);

	}

	private void configureNature(final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering configureNature");
		final IProject project = this.nature.getProject();
		// We have to explicitly add the nature.
		final IProjectDescription desc = project.getDescription();
		final String[] natures = desc.getNatureIds();
		for (int i = 0; i < natures.length; i++) {
			if (natureId.equals(natures[i])) {
				// already configured
				return;
			}
		}
		final String[] newNatures = Arrays.copyOf(natures, natures.length + 1);
		newNatures[natures.length] = natureId;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);
	}

	private void deconfigureNature(final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering deconfigureNature");
		// remove the nature itself, by resetting the nature list.
		final IProject project = this.nature.getProject();
		final IProjectDescription desc = project.getDescription();
		final String[] natures = desc.getNatureIds();
		final List<String> newNaturesList = new ArrayList<>();
		for (int i = 0; i < natures.length; i++) {
			if (!natureId.equals(natures[i])) {
				newNaturesList.add(natures[i]);
			}
		}

		final String[] newNatures =
		        newNaturesList.toArray(new String[newNaturesList.size()]);
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);
	}

	private void deleteEclipseFiles(final IProgressMonitor monitor)
	        throws CoreException {
		LOG.debug("entering deleteEclipseFiles");
		final IProject project = this.nature.getProject();
		final IResource findbugsFile = project.getFile(associatedFileName);
		findbugsFile.delete(IResource.FORCE, monitor);

	}
}
