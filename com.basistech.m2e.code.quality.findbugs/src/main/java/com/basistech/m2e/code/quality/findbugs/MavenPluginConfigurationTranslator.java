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

import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.FB_EXCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.FB_INCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.INCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.LOG_PREFIX;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.BUG_CATEGORIES;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.EFFORT;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.EXCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.OMIT_VISITORS;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.PRIORITY;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.THRESHOLD;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.VISITORS;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.DEBUG;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.MAX_RANK;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.io.URLInputStreamFacade;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.ConfigurationException;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;
import com.google.common.base.Preconditions;

import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;

/**
 * Utility class to get findbugs plugin configuration.
 */
public class MavenPluginConfigurationTranslator {

    private static final Logger log = LoggerFactory
        .getLogger("com/basistech/m2e/code/quality/findbugs/MavenPluginConfigurationTranslator");
    private final IProject project;
    private final AbstractMavenPluginProjectConfigurator configurator;
    private final ResourceResolver resourceResolver;
    private final MavenSession session;
    private final MojoExecution execution;

    private MavenPluginConfigurationTranslator(final AbstractMavenPluginProjectConfigurator configurator,
            final MavenSession session, final MavenProject mavenProject, final MavenPluginWrapper pluginWrapper,
            final IProject project) throws CoreException {
        this.project = project;
        this.resourceResolver = ResourceResolver.newInstance(configurator.getPluginClassRealm(session,
            pluginWrapper.getMojoExecution()));
        this.session = session;
        this.execution = pluginWrapper.getMojoExecution();
        this.configurator = configurator;
    }

    public void setIncludeFilterFiles(final UserPreferences prefs) throws CoreException {
        final String includeFilterFile = this.configurator.getParameterValue(INCLUDE_FILTER_FILE, String.class,
            session, execution);
        // don't do anything if null
        if (includeFilterFile == null) {
            log.debug("includeFilterFile is null");
            return;
        }
        this.copyUrlResourceToProject(includeFilterFile, FB_INCLUDE_FILTER_FILE);
        final Map<String, Boolean> curIncludeFilteredFiles = prefs.getIncludeFilterFiles();
        final Map<String, Boolean> newIncludeFilteredFiles = new HashMap<String, Boolean>();
        // Make sure we add it only once.
        if (!curIncludeFilteredFiles.containsKey(FB_INCLUDE_FILTER_FILE)) {
            newIncludeFilteredFiles.put(FB_INCLUDE_FILTER_FILE, Boolean.TRUE);
        }
        newIncludeFilteredFiles.putAll(curIncludeFilteredFiles);
        prefs.setIncludeFilterFiles(newIncludeFilteredFiles);
    }

    public void setExcludeFilterFiles(final UserPreferences prefs) throws CoreException {
        log.debug("entering setExcludeFilterFiles");
        final String excludeFilterFile = this.configurator.getParameterValue(EXCLUDE_FILTER_FILE, String.class,
            session, execution);
        // don't do anything if null
        if (excludeFilterFile == null) {
            log.debug("excludeFilterFile is null");
            return;
        }
        this.copyUrlResourceToProject(excludeFilterFile, FB_EXCLUDE_FILTER_FILE);
        final Map<String, Boolean> curExcludeFilteredFiles = prefs.getExcludeFilterFiles();
        final Map<String, Boolean> newExcludeFilteredFiles = new HashMap<String, Boolean>();
        // Make sure we add it only once.
        if (!curExcludeFilteredFiles.containsKey(FB_EXCLUDE_FILTER_FILE)) {
            newExcludeFilteredFiles.put(FB_EXCLUDE_FILTER_FILE, Boolean.TRUE);
        }
        newExcludeFilteredFiles.putAll(curExcludeFilteredFiles);
        prefs.setExcludeFilterFiles(newExcludeFilteredFiles);
    }

    /**
     * Set the Bug Categories we are interested, if configured.
     * <p>
     * <ul>
     * <li>CORRECTNESS</li>
     * <li>NOISE</li>
     * <li>SECURITY</li>
     * <li>BAD_PRACTICE</li>
     * <li>STYLE</li>
     * <li>PERFORMANCE</li>
     * <li>MALICIOUS_CODE</li>
     * <li>MT_CORRECTNESS</li>
     * <li>I18N</li>
     * <li>EXPERIMENTAL</li>
     * </ul>
     * </p>
     * 
     * @param prefs the {@link UserPreferences} instance.
     */
    public void setBugCatagories(final UserPreferences prefs) throws CoreException {
        final ProjectFilterSettings pfs = prefs.getFilterSettings();
        final String bugCatagories = this.configurator.getParameterValue(BUG_CATEGORIES, String.class, session,
            execution);
        if (bugCatagories == null) {
            log.debug("bugCatagories is null");
            return;
        }
        List<String> addBugCatagoriesList = Arrays.asList(StringUtils.split(bugCatagories, ","));
        List<String> availableBugCategories = new LinkedList<String>(DetectorFactoryCollection.instance()
            .getBugCategories());
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
                log.debug(String.format("[%s]: Unknown Bug Catagory [%s]", LOG_PREFIX, bc));
            }
            if (pfs.getActiveCategorySet().contains(bcUpper)) {
                removeBugCategoriesSet.add(bcUpper);
            }
        }
    }

    public boolean debugEnabled() throws CoreException {
        return this.configurator.getParameterValue(DEBUG, Boolean.class, session, execution);
    }

    public void setEffort(final UserPreferences prefs) throws CoreException {
        String effort = this.configurator.getParameterValue(EFFORT, String.class, session, execution);
        if (effort == null) {
            log.debug("effort is null");
            return;
        }
        // lowercase
        effort = effort.toLowerCase();
        try {
            prefs.setEffort(effort);
        } catch (final IllegalArgumentException ex) {
            log.error(String.format("[%s]: could not set <effort>, reason [%s], setting it to default [%s]",
                LOG_PREFIX, effort, UserPreferences.EFFORT_DEFAULT));
        }
    }

    public void setMinRank(final UserPreferences prefs) throws CoreException {
        Integer minRank = this.configurator.getParameterValue(MAX_RANK, Integer.class, session, execution);
        if (minRank == null) {
            log.debug("max rank is null");
            return;
        }
        try {
            prefs.getFilterSettings().setMinRank(minRank);
        } catch (final IllegalArgumentException ex) {
            log.error(String.format("[%s]: could not set <rank>, reason [%s], setting it to default [%s]", LOG_PREFIX,
                minRank, 15));
        }
    }

    public void setPriority(final UserPreferences prefs) throws CoreException {
        final String priority = this.configurator.getParameterValue(PRIORITY, String.class, session, execution);
        if (priority == null) {
            log.debug("priority is null");
            return;
        }
        try {
            prefs.getFilterSettings().setMinPriority(priority);
        } catch (final Exception ex) {
            log.error(String.format("[%s]: could not set <threshold>, reason [%s], leaving it alone", LOG_PREFIX,
                priority));
        }
    }

    public void setOmitVisitors(final UserPreferences prefs) throws CoreException {
        final String omitVisitors = this.configurator
            .getParameterValue(OMIT_VISITORS, String.class, session, execution);
        if (omitVisitors == null) {
            log.debug("omitVisitors is null");
            return;
        }
        List<String> detectorsList = Arrays.asList(StringUtils.split(omitVisitors, ","));
        final DetectorFactoryCollection dfc = DetectorFactoryCollection.instance();
        for (String d : detectorsList) {
            final DetectorFactory df = dfc.getFactory(d);
            if (df == null) {
                log.error(String.format("[%s]: IGNORING unknown detector [%s]", LOG_PREFIX, d));
            } else {
                prefs.enableDetector(df, false);
            }
        }
    }

    public void setThreshold(final UserPreferences prefs) throws CoreException {
        final String threshold = this.configurator.getParameterValue(THRESHOLD, String.class, session, execution);
        if (threshold == null) {
            log.debug("threshold is null");
            return;
        }
        try {
            prefs.getFilterSettings().setMinPriority(threshold);
        } catch (final Exception ex) {
            log.error(String.format("[%s]: could not set <threshold>, reason [%s], leaving it alone", LOG_PREFIX,
                threshold));
        }
    }

    public void setVisitors(final UserPreferences prefs) throws CoreException {
        final String visitors = this.configurator.getParameterValue(VISITORS, String.class, session, execution);
        if (visitors == null) {
            return;
        }
        List<String> detectorsList = Arrays.asList(StringUtils.split(visitors, ","));
        prefs.enableAllDetectors(false);
        final DetectorFactoryCollection dfc = DetectorFactoryCollection.instance();
        for (String d : detectorsList) {
            final DetectorFactory df = dfc.getFactory(d);
            if (df == null) {
                log.error(String.format("[%s]: IGNORING unknown detector [%s]", LOG_PREFIX, d));
            } else {
                prefs.enableDetector(df, true);
            }
        }
    }

    /**
     * Copy a resource from the maven plugin configuration to a location within the project.
     * <p>
     * This the only reference I could find on how the Findbugs Eclipse Plugin configuration works.
     * </p>
     * 
     * @param resc the resource location as read from the plugin configuration.
     * @param newLocation the new location relative to the project root.
     * @throws NullPointerException If any of the arguments are {@code null}.
     * @throws ConfigurationException If an error occurred during the resolution of the resource or copy to the new
     *             location failed.
     */
    private void copyUrlResourceToProject(final String resc, final String newLocation) {
        log.debug("entering copyUrlResourceToProject");
        Preconditions.checkNotNull(resc);
        Preconditions.checkNotNull(newLocation);
        final URL urlResc = this.resourceResolver.resolveLocation(resc);
        if (urlResc == null) {
            throw new ConfigurationException(String.format("[%s]: could not locate resource [%s]", LOG_PREFIX, resc));
        }
        // copy the file to new location
        final File newLocationFile = new File(this.project.getLocationURI().getPath(), newLocation);
        try {
            FileUtils.copyStreamToFile(new URLInputStreamFacade(urlResc), newLocationFile);
        } catch (IOException ex) {
            throw new ConfigurationException(String.format("[%s]: could not copy resource [%s] to [%s], reason [%s]",
                LOG_PREFIX, resc, newLocationFile, ex.getLocalizedMessage()));
        }
    }

    public static MavenPluginConfigurationTranslator newInstance(AbstractMavenPluginProjectConfigurator configurator,
            MavenSession session, final MavenProject mavenProject, final MavenPluginWrapper mavenPlugin,
            final IProject project) throws CoreException {
        final MavenPluginConfigurationTranslator m2csConverter = new MavenPluginConfigurationTranslator(configurator,
            session, mavenProject, mavenPlugin, project);
        return m2csConverter;
    }
}
