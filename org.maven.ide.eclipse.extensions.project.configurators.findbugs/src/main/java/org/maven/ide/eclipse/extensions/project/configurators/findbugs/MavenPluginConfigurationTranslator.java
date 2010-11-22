package org.maven.ide.eclipse.extensions.project.configurators.findbugs;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.io.URLInputStreamFacade;
import org.eclipse.core.resources.IProject;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.core.MavenConsole;
import org.maven.ide.eclipse.extensions.shared.util.ConfigurationException;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;
import org.maven.ide.eclipse.extensions.shared.util.ResourceResolver;

import com.google.common.base.Preconditions;

import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.I18N;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;

import static org.maven.ide.eclipse.extensions.project.configurators.findbugs.FindbugsEclipseConstants.*;

public class MavenPluginConfigurationTranslator {
    
    private static final String DEF_VALUE_SEPARATOR = ",";
    @SuppressWarnings("unused")
    private final MavenProject mavenProject;
    private final MavenConsole console;
    private final IProject project;
    private final MavenPluginConfigurationExtractor cfgExtractor;
    private final ResourceResolver resourceResolver;
    
    private MavenPluginConfigurationTranslator(
            final MavenProject mavenProject,
            final MavenPluginWrapper pluginWrapper,
            final IProject project) {
        this.console = MavenPlugin.getDefault().getConsole();
        this.mavenProject = mavenProject;
        this.project = project;
        this.cfgExtractor = MavenPluginConfigurationExtractor.newInstance(pluginWrapper);
        this.resourceResolver = ResourceResolver.newInstance(pluginWrapper, LOG_PREFIX);
    }

    public List<String> getVisitors() {
        return this.cfgExtractor.splitValueAsList(null, "visitors", DEF_VALUE_SEPARATOR);
    }
    
    public List<String> getPluginList() {
        return this.cfgExtractor.splitValueAsList(null, "pluginList", DEF_VALUE_SEPARATOR);
    }

    public List<String> getOnlyAnalyze() {
        return this.cfgExtractor.splitValueAsList(null, "onlyAnalyze", DEF_VALUE_SEPARATOR);
    }

    public boolean includeTests() {
        return this.cfgExtractor.asBoolean(null, "includeTests");
    }

    public boolean debugEnabled() {
        return this.cfgExtractor.asBoolean(null, "debug");
    }
    
    public void setThreshold(final UserPreferences prefs) {
        final String threshold = this.cfgExtractor.value(null, "threshold");
        try {
            prefs.getFilterSettings().setMinPriority(threshold);
        } catch (final Exception ex) {
            this.console.logError(String.format(
                    "[%s]: could not set <threshold>, reason [%s], leaving it alone",
                    LOG_PREFIX, threshold));
        }
    }

    public void setVisitors(final UserPreferences prefs) {
        final List<String> detectorsList = this.cfgExtractor
            .splitValueAsList(null, "visitors", DEF_VALUE_SEPARATOR);
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

    public void setOmitVisitors(final UserPreferences prefs) {
        final List<String> detectorsList = this.cfgExtractor
            .splitValueAsList(null, "omitVisitors", DEF_VALUE_SEPARATOR);
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

    public void setPriority(final UserPreferences prefs) {
        final String priority = this.cfgExtractor.value(null, "priority");
        try {
            prefs.getFilterSettings().setMinPriority(priority);
        } catch (final Exception ex) {
            this.console.logError(String.format(
                    "[%s]: could not set <threshold>, reason [%s], leaving it alone",
                    LOG_PREFIX, priority));
        }
    }
    
    public void setEffort(final UserPreferences prefs) {
        String effort = this.cfgExtractor.value(null, "effort");
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
    
    public void setIncludeFilterFiles(final UserPreferences prefs) {
        final String includeFilterFile = this.cfgExtractor.value(null, "includeFilterFile");
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

    public void setExcludeFilterFiles(final UserPreferences prefs) {
        final String excludeFilterFile = this.cfgExtractor.value(null, "excludeFilterFile");
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
     */
    public void setBugCatagories(final UserPreferences prefs) {
        final ProjectFilterSettings pfs = prefs.getFilterSettings();
        final List<String> addBugCatagoriesList = this.cfgExtractor
            .splitValueAsList(null, "bugCategories", DEF_VALUE_SEPARATOR);
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
            final MavenProject mavenProject,
            final MavenPluginWrapper pluginWrapper,
            final IProject project) {
        final MavenPluginConfigurationTranslator instance = 
            new MavenPluginConfigurationTranslator(
                    mavenProject, pluginWrapper,
                    project);
        //instance.initialize();
        return instance;
    }
}
