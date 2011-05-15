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
package com.basistech.m2e.code.quality.checkstyle;

import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_CACHE_FILENAME;
import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_PREFS_FILE;

import java.util.ArrayList;
import java.util.List;

import net.sf.eclipsecs.core.nature.CheckstyleNature;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.google.common.base.Preconditions;
/**
 * An extension of the {@code Checkstyle} class to add missing functionality.
 * 
 */
public class EclipseCheckstyleConfigManager {

    private final CheckstyleNature csNature;
    
    private EclipseCheckstyleConfigManager(final CheckstyleNature csNature) {
        this.csNature = csNature;
    }
    
    public void configure(final IProgressMonitor monitor) throws CoreException {
        //this adds the builder only.
        this.csNature.configure();
        this.configureNature(monitor);
    }

    public void deconfigure(final IProgressMonitor monitor) throws CoreException {
        //this removes the builder only.
        this.csNature.deconfigure();
        this.deconfigureNature(monitor);
        //remove all eclipse checkstyle files.
        this.deleteEclipseFiles(monitor);
        
    }

    private void configureNature(final IProgressMonitor monitor) throws CoreException {
        final IProject project = this.csNature.getProject();
        //We have to explicitly add the nature.
        final IProjectDescription desc = project.getDescription();
        final String natures[] = desc.getNatureIds();
        final String newNatures[] = new String[natures.length + 1];
        System.arraycopy(natures, 0, newNatures, 0, natures.length);
        newNatures[natures.length] = CheckstyleNature.NATURE_ID;
        desc.setNatureIds(newNatures);
        project.setDescription(desc, monitor);
    }
    
    private void deconfigureNature(final IProgressMonitor monitor) throws CoreException {
        //remove the nature itself, by resetting the nature list.
        final IProject project = this.csNature.getProject();
        final IProjectDescription desc = project.getDescription();
        final String natures[] = desc.getNatureIds();
        final List<String> newNaturesList = new ArrayList<String>();
        for (int i = 0; i < natures.length; i++) {
            if(!CheckstyleNature.NATURE_ID.equals(natures[i]))
                newNaturesList.add(natures[i]);
        }

        final String newNatures[] = newNaturesList.toArray(new String[newNaturesList.size()]);
        desc.setNatureIds(newNatures);
        project.setDescription(desc, monitor);
    }

    private void deleteEclipseFiles(final IProgressMonitor monitor) throws CoreException {
        final IProject project = this.csNature.getProject();
        final IResource checkstyleFile = project.getFile(ECLIPSE_CS_PREFS_FILE);
        checkstyleFile.delete(IResource.FORCE, monitor);
        final IResource checkstyleCacheFileResource = project
            .getFile(ECLIPSE_CS_CACHE_FILENAME);
        checkstyleCacheFileResource.delete(IResource.FORCE, monitor);
        
    }
    
    public static EclipseCheckstyleConfigManager newInstance(final IProject project) {
        Preconditions.checkNotNull(project);
        final CheckstyleNature csNature = new CheckstyleNature();
        csNature.setProject(project);
        final EclipseCheckstyleConfigManager csNatureExtended = 
            new EclipseCheckstyleConfigManager(csNature);
        return csNatureExtended;
    }
}
