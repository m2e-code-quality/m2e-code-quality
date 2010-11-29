/*******************************************************************************
 * Copyright 2010 Mohan KR
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
package org.maven.ide.eclipse.extensions.project.configurators.findbugs;

import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.FB_EXCLUDE_FILTER_FILE;
import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.FB_INCLUDE_FILTER_FILE;
import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.LOG_PREFIX;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.io.URLInputStreamFacade;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.extensions.shared.util.ConfigurationException;
import org.maven.ide.eclipse.extensions.shared.util.ResourceResolver;

import com.google.common.base.Preconditions;

import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.I18N;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;

public class MavenPluginConfigurationTranslator {
    
    private final MavenConsole console;
    private final MavenSession session;
    private final IProject project;
    private final MojoExecution findbugsExecution;
    private final EclipseFindbugsProjectConfigurator configurator;

    private final ResourceResolver resourceResolver;
    
    private MavenPluginConfigurationTranslator(
    		final EclipseFindbugsProjectConfigurator configurator,
    		final MavenSession session,
    		MojoExecution findbugsExecution, 
    		final IProject project) throws CoreException {
    	this.configurator = configurator;
    	this.session = session;
    	this.findbugsExecution = findbugsExecution;
        this.console = MavenPlugin.getDefault().getConsole();
        this.project = project;
        this.resourceResolver = ResourceResolver.newInstance(configurator.getPluginClassRealm(session, 
    			findbugsExecution), LOG_PREFIX);
    }

    public List<String> getVisitors() throws CoreException {
    	return configurator.getCommaSeparatedStringParameterValues("visitors", session, findbugsExecution);
    }
    
    public List<String> getPluginList() throws CoreException {
    	return configurator.getCommaSeparatedStringParameterValues("pluginList", session, findbugsExecution);
    }

    public List<String> getOnlyAnalyze() throws CoreException {
    	return configurator.getCommaSeparatedStringParameterValues("onlyAnalyze", session, findbugsExecution);
    }

    public boolean includeTests() throws CoreException {
    	Boolean val = configurator.getParameterValue("includeTests", Boolean.class, session, findbugsExecution);
    	return val != null && val.booleanValue();
    }

    public boolean debugEnabled() throws CoreException {
    	Boolean val = configurator.getParameterValue("debug", Boolean.class, session, findbugsExecution);
    	return val != null && val.booleanValue();
    }
    
    public void setThreshold(final UserPreferences prefs) throws CoreException {
        final String threshold = configurator.getParameterValue("debug", String.class, session, findbugsExecution);
        if (threshold != null) {
        	try {
        		prefs.getFilterSettings().setMinPriority(threshold);
        	} catch (final Exception ex) {
        		this.console.logError(String.format(
        				"[%s]: could not set <threshold>, reason [%s], leaving it alone",
        				LOG_PREFIX, threshold));
        	}
        }
    }

    public void setVisitors(final UserPreferences prefs) throws CoreException {
    	final List<String> detectorsList = configurator.getCommaSeparatedStringParameterValues("visitors", session, findbugsExecution);
        if (detectorsList.isEmpty()) {
            return;
        }
        prefs.enableAllDetectors(false);
        final DetectorFactoryCollection dfc = DetectorFactoryCollection.instance();
        for (String d : detectorsList) {
            final DetectorFactory df = dfc.getFactory(d);
            if (df == null) {
                this.console.logError(String.format(
                        "[%s]: IGNORING unknown detector [%s]", LOG_PREFIX, d));
            } else {
                prefs.enableDetector(df, true);
            }
        }
    }

    public void setOmitVisitors(final UserPreferences prefs) throws CoreException {
        final List<String> detectorsList = configurator.getCommaSeparatedStringParameterValues("omitVisitors", session, findbugsExecution);
        if (detectorsList.isEmpty()) {
            return;
        }
        final DetectorFactoryCollection dfc = DetectorFactoryCollection.instance();
        for (String d : detectorsList) {
            final DetectorFactory df = dfc.getFactory(d);
            if (df == null) {
                this.console.logError(String.format(
                        "[%s]: IGNORING unknown detector [%s]", LOG_PREFIX, d));
            } else {
                prefs.enableDetector(df, false);
            }
        }
    }

    public void setPriority(final UserPreferences prefs) throws CoreException {
        final String priority = configurator.getParameterValue("priority", String.class, session, findbugsExecution);
        if (priority != null) {
        	try {
        		prefs.getFilterSettings().setMinPriority(priority);
        	} catch (final Exception ex) {
        		this.console.logError(String.format(
        				"[%s]: could not set <threshold>, reason [%s], leaving it alone",
        				LOG_PREFIX, priority));
        	}
        }
    }
    
    public void setEffort(final UserPreferences prefs) throws CoreException {
        String effort = configurator.getParameterValue("effort", String.class, session, findbugsExecution);
        if (effort == null) {
            return;
        }
        //lowercase
        effort = effort.toLowerCase();
        try {
            prefs.setEffort(effort);
        } catch (final IllegalArgumentException ex) {
            this.console.logError(String.format(
               "[%s]: could not set <effort>, reason [%s], setting it to default [%s]",
               LOG_PREFIX, effort, UserPreferences.EFFORT_DEFAULT
            ));
        }
    }
    
    public void setIncludeFilterFiles(final UserPreferences prefs) throws CoreException {
        final String includeFilterFile = configurator.getParameterValue("includeFileFilter", String.class, session, findbugsExecution);
        //don't do anything if null
        if (includeFilterFile == null) {
            return;
        }
        this.copyUrlResourceToProject(includeFilterFile, 
                FB_INCLUDE_FILTER_FILE);
        final Collection<String> curIncludeFilteredFiles = prefs.getIncludeFilterFiles();
        final List<String> newIncludeFilteredFiles = new ArrayList<String>();
        //Make sure we add it only once.
        if (!curIncludeFilteredFiles.contains(FB_INCLUDE_FILTER_FILE)) {
            newIncludeFilteredFiles.add(FB_INCLUDE_FILTER_FILE);
        }
        newIncludeFilteredFiles.addAll(curIncludeFilteredFiles);
        prefs.setIncludeFilterFiles(newIncludeFilteredFiles);
    }

    public void setExcludeFilterFiles(final UserPreferences prefs) throws CoreException {
        final String excludeFilterFile = configurator.getParameterValue("excludeFileFilter", String.class, session, findbugsExecution);
        //don't do anything if null
        if (excludeFilterFile == null) {
            return;
        }
        this.copyUrlResourceToProject(excludeFilterFile, 
                FB_EXCLUDE_FILTER_FILE);
        final Collection<String> curExcludeFilteredFiles = prefs.getExcludeFilterFiles();
        final List<String> newExcludeFilteredFiles = new ArrayList<String>();
        //Make sure we add it only once.
        if (!curExcludeFilteredFiles.contains(FB_EXCLUDE_FILTER_FILE)) {
            newExcludeFilteredFiles.add(FB_EXCLUDE_FILTER_FILE);
        }
        newExcludeFilteredFiles.addAll(curExcludeFilteredFiles);
        prefs.setExcludeFilterFiles(newExcludeFilteredFiles);
    }

    /**
     * Set the Bug Categories we are interested, if configured.
     * 
     * <p>
     *  <ul>
     *    <li>CORRECTNESS</li>
     *    <li>NOISE</li>
     *    <li>SECURITY</li>
     *    <li>BAD_PRACTICE</li>
     *    <li>STYLE</li>
     *    <li>PERFORMANCE</li>
     *    <li>MALICIOUS_CODE</li>
     *    <li>MT_CORRECTNESS</li>
     *    <li>I18N</li>
     *    <li>EXPERIMENTAL</li>
     *  </ul>
     * </p>
     * @param prefs the {@link UserPreferences} instance.
     * @throws CoreException 
     */
    public void setBugCatagories(final UserPreferences prefs) throws CoreException {
        final ProjectFilterSettings pfs = prefs.getFilterSettings();
        final List<String> addBugCatagoriesList = 
        	configurator.getCommaSeparatedStringParameterValues("bugCategories", session, findbugsExecution);
        final List<String> availableBugCategories = 
            new LinkedList<String>(I18N.instance().getBugCategories());
        if (addBugCatagoriesList.size() > 0) {
            for (String removeBugCategory : availableBugCategories) {
                pfs.removeCategory(removeBugCategory);
            }
        }
        final Set<String> removeBugCategoriesSet = new HashSet<String>();
        for (String bc : addBugCatagoriesList) {
            final String bcUpper = bc.toUpperCase();
            if (availableBugCategories.contains(bcUpper)) {
                pfs.addCategory(bcUpper);
            } else {
                this.console.logError(String.format("[%s]: Unknown Bug Catagory [%s]", LOG_PREFIX, bc));
            }
            if (pfs.getActiveCategorySet().contains(bcUpper)) {
                removeBugCategoriesSet.add(bcUpper);
            }
        }
    }
    
    /**
     * Copy a resource from the maven plugin configuration to a location within the project.
     * 
     * <p>
     *  This the only reference I could find on how the Findbugs Eclipse Plugin configuration works.
     * </p>
     * 
     * @param resc        the resource location as read from the plugin configuration.
     * @param newLocation the new location relative to the project root.
     * 
     * @throws NullPointerException   If any of the arguments are {@code null}.
     * @throws ConfigurationException If an error occurred during the resolution of the resource
     *                                or copy to the new location failed.
     */
    private void copyUrlResourceToProject(final String resc, final String newLocation) {
        Preconditions.checkNotNull(resc);
        Preconditions.checkNotNull(newLocation);
        final URL urlResc = this.resourceResolver.resolveLocation(resc);
        if (urlResc == null) {
            throw new ConfigurationException(
              String.format("[%s]: could not locate resource [%s]", LOG_PREFIX, resc));
        }
        //copy the file to new location
        final File newLocationFile = new File(this.project.getLocationURI().getPath(), newLocation);
        try {
            FileUtils.copyStreamToFile(new URLInputStreamFacade(urlResc), 
                    newLocationFile);
        } catch (IOException ex) {
            throw new ConfigurationException(String.format(
                    "[%s]: could not copy resource [%s] to [%s], reason [%s]",
                    LOG_PREFIX, resc, newLocationFile, ex.getLocalizedMessage()));
        }
        this.console.logMessage(String.format(
                "[%s]: Storing specified resource <%s>, in project as <%s>",
                LOG_PREFIX, resc, newLocationFile));
    }

    public static MavenPluginConfigurationTranslator newInstance(
    		EclipseFindbugsProjectConfigurator configurator,
    		MavenSession session,
            MojoExecution findbugsExecution,
            final IProject project) throws CoreException {
        final MavenPluginConfigurationTranslator m2csConverter =
            new MavenPluginConfigurationTranslator(
                    configurator, 
                    session, 
                    findbugsExecution,
                    project);
        return m2csConverter;
    }
}
