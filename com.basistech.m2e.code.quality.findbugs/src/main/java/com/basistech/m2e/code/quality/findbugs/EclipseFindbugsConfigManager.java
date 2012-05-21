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

import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.ECLIPSE_FB_NATURE_ID;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.ECLIPSE_FB_PREFS_FILE;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import de.tobject.findbugs.nature.FindBugsNature;

/**
 * An extension of the {@code Findbugs} class to add missing functionality.
 * 
 */
public class EclipseFindbugsConfigManager {

	private static final Logger log = LoggerFactory
			.getLogger("com/basistech/m2e/code/quality/findbugs/EclipseFindbugsConfigManager");

	private final FindBugsNature fbNature;

	private EclipseFindbugsConfigManager(final FindBugsNature fbNature) {
		this.fbNature = fbNature;
	}

	public void configure(final IProgressMonitor monitor) throws CoreException {
		log.debug("entering configure");
		// this adds the builder only.
		this.fbNature.configure();
		this.configureNature(monitor);
	}

	public void deconfigure(final IProgressMonitor monitor)
			throws CoreException {
		log.debug("entering deconfigure");
		// this removes the builder only.
		this.fbNature.deconfigure();
		this.deconfigureNature(monitor);
		// remove all eclipse checkstyle files.
		this.deleteEclipseFiles(monitor);

	}

	private void configureNature(final IProgressMonitor monitor)
			throws CoreException {
		log.debug("entering configureNature");
		final IProject project = this.fbNature.getProject();
		// We have to explicitly add the nature.
		final IProjectDescription desc = project.getDescription();
		final String natures[] = desc.getNatureIds();
		final String newNatures[] = new String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = ECLIPSE_FB_NATURE_ID;
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);
	}

	private void deconfigureNature(final IProgressMonitor monitor)
			throws CoreException {
		log.debug("entering deconfigureNature");
		// remove the nature itself, by resetting the nature list.
		final IProject project = this.fbNature.getProject();
		final IProjectDescription desc = project.getDescription();
		final String natures[] = desc.getNatureIds();
		final List<String> newNaturesList = new ArrayList<String>();
		for (int i = 0; i < natures.length; i++) {
			if (!ECLIPSE_FB_NATURE_ID.equals(natures[i]))
				newNaturesList.add(natures[i]);
		}

		final String newNatures[] = newNaturesList
				.toArray(new String[newNaturesList.size()]);
		desc.setNatureIds(newNatures);
		project.setDescription(desc, monitor);
	}

	private void deleteEclipseFiles(final IProgressMonitor monitor)
			throws CoreException {
		log.debug("entering deleteEclipseFiles");
		final IProject project = this.fbNature.getProject();
		final IResource findbugsFile = project.getFile(ECLIPSE_FB_PREFS_FILE);
		findbugsFile.delete(IResource.FORCE, monitor);

	}

	public static EclipseFindbugsConfigManager newInstance(
			final IProject project) {
		log.debug("entering newInstance");
		Preconditions.checkNotNull(project);
		final FindBugsNature fbNature = new FindBugsNature();
		fbNature.setProject(project);
		final EclipseFindbugsConfigManager fbNatureExtended = new EclipseFindbugsConfigManager(
				fbNature);
		return fbNatureExtended;
	}
}
