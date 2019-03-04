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

import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.BUG_CATEGORIES;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.DEBUG;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.EFFORT;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.EXCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.FB_EXCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.FB_INCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.INCLUDE_FILTER_FILE;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.LOG_PREFIX;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.MAX_RANK;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.OMIT_VISITORS;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.PRIORITY;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.THRESHOLD;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.VISITORS;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.SKIP;

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
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.embedder.IMaven;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginConfigurationTranslator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;

/**
 * Utility class to get findbugs plugin configuration.
 */
public class MavenPluginConfigurationTranslator extends AbstractMavenPluginConfigurationTranslator {

	private static final Logger LOG =
	        LoggerFactory.getLogger(MavenPluginConfigurationTranslator.class);

	private MavenPluginConfigurationTranslator(
	        final IMaven maven,
	        final MavenSession session, final MavenProject mavenProject,
	        final MojoExecution execution, final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		super(maven, session, mavenProject, execution, project, monitor);
	}

	public boolean isSkip() throws CoreException {
		return getParameterValue(SKIP, Boolean.class, Boolean.FALSE);
	}

	public void setIncludeFilterFiles(final UserPreferences prefs)
	        throws CoreException {
		final String includeFilterFile =
		        getParameterValue(INCLUDE_FILTER_FILE, String.class);
		// don't do anything if null
		if (includeFilterFile == null) {
			LOG.debug("includeFilterFile is null");
			return;
		}
		List<String> filterFiles = this.copyUrlResourcesToProject(includeFilterFile,
		        FB_INCLUDE_FILTER_FILE);
		final Map<String, Boolean> curIncludeFilteredFiles =
		        prefs.getIncludeFilterFiles();
		final Map<String, Boolean> newIncludeFilteredFiles = new HashMap<>();
		// Make sure we add it only once.
		for (String filterFile : filterFiles) {
			if (!curIncludeFilteredFiles.containsKey(filterFile)) {
				newIncludeFilteredFiles.put(filterFile, Boolean.TRUE);
			}
		}
		newIncludeFilteredFiles.putAll(curIncludeFilteredFiles);
		prefs.setIncludeFilterFiles(newIncludeFilteredFiles);
	}

	public void setExcludeFilterFiles(final UserPreferences prefs)
	        throws CoreException {
		LOG.debug("entering setExcludeFilterFiles");
		final String excludeFilterFile =
		        getParameterValue(EXCLUDE_FILTER_FILE, String.class);
		// don't do anything if null
		if (excludeFilterFile == null) {
			LOG.debug("excludeFilterFile is null");
			return;
		}

		List<String> filterFiles = this.copyUrlResourcesToProject(excludeFilterFile,
		        FB_EXCLUDE_FILTER_FILE);
		final Map<String, Boolean> curExcludeFilteredFiles =
				prefs.getExcludeFilterFiles();
		final Map<String, Boolean> newExcludeFilteredFiles = new HashMap<>();
		// Make sure we add it only once.
		for (String filterFile : filterFiles) {
			if (!curExcludeFilteredFiles.containsKey(filterFile)) {
				newExcludeFilteredFiles.put(filterFile, Boolean.TRUE);
			}
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
	 * @param prefs
	 *            the {@link UserPreferences} instance.
	 * @throws CoreException
	 *             if an error occurs
	 */
	public void setBugCatagories(final UserPreferences prefs)
	        throws CoreException {
		final ProjectFilterSettings pfs = prefs.getFilterSettings();
		final String bugCatagories = getParameterValue(BUG_CATEGORIES, String.class);
		if (bugCatagories == null) {
			LOG.debug("bugCatagories is null");
			return;
		}
		final List<String> addBugCatagoriesList =
		        Arrays.asList(StringUtils.split(bugCatagories, ","));
		final List<String> availableBugCategories = new LinkedList<>(
		        DetectorFactoryCollection.instance().getBugCategories());
		if (!addBugCatagoriesList.isEmpty()) {
			for (final String removeBugCategory : availableBugCategories) {
				pfs.removeCategory(removeBugCategory);
			}
		}
		final Set<String> removeBugCategoriesSet = new HashSet<>();
		for (final String bc : addBugCatagoriesList) {
			final String bcUpper = bc.toUpperCase();
			if (availableBugCategories.contains(bcUpper)) {
				pfs.addCategory(bcUpper);
			} else {
				LOG.debug(String.format("[%s]: Unknown Bug Catagory [%s]",
				        LOG_PREFIX, bc));
			}
			if (pfs.getActiveCategorySet().contains(bcUpper)) {
				removeBugCategoriesSet.add(bcUpper);
			}
		}
	}

	public boolean debugEnabled() throws CoreException {
		return getParameterValue(DEBUG, Boolean.class);
	}

	public void setEffort(final UserPreferences prefs) throws CoreException {
		String effort = getParameterValue(EFFORT, String.class);
		if (effort == null) {
			LOG.debug("effort is null");
			return;
		}
		// lowercase
		effort = effort.toLowerCase();
		try {
			prefs.setEffort(effort);
		} catch (final IllegalArgumentException ex) {
			LOG.error(
			        "{}: could not set <effort>, reason {}, setting it to default {}",
			        LOG_PREFIX, effort, UserPreferences.EFFORT_DEFAULT, ex);
		}
	}

	public void setMinRank(final UserPreferences prefs) throws CoreException {
		final Integer minRank = getParameterValue(MAX_RANK, Integer.class);
		if (minRank == null) {
			LOG.debug("max rank is null");
			return;
		}
		try {
			prefs.getFilterSettings().setMinRank(minRank);
		} catch (final IllegalArgumentException ex) {
			LOG.error(
			        "{}: could not set <rank>, reason {}, setting it to default {}",
			        LOG_PREFIX, minRank, 15, ex);
		}
	}

	public void setPriority(final UserPreferences prefs) throws CoreException {
		final String priority = getParameterValue(PRIORITY, String.class);
		if (priority == null) {
			LOG.debug("priority is null");
			return;
		}
		try {
			prefs.getFilterSettings().setMinPriority(priority);
		} catch (final Exception ex) {
			LOG.error(
			        "{}: could not set <threshold>, reason {}, leaving it alone",
			        LOG_PREFIX, priority, ex);
		}
	}

	public void setOmitVisitors(final UserPreferences prefs)
	        throws CoreException {
		final String omitVisitors = getParameterValue(OMIT_VISITORS, String.class);
		if (omitVisitors == null) {
			LOG.debug("omitVisitors is null");
			return;
		}
		final List<String> detectorsList =
		        Arrays.asList(StringUtils.split(omitVisitors, ","));
		final DetectorFactoryCollection dfc =
		        DetectorFactoryCollection.instance();
		for (final String d : detectorsList) {
			final DetectorFactory df = dfc.getFactory(d);
			if (df == null) {
				LOG.error(String.format("[%s]: IGNORING unknown detector [%s]",
				        LOG_PREFIX, d));
			} else {
				prefs.enableDetector(df, false);
			}
		}
	}

	public void setThreshold(final UserPreferences prefs) throws CoreException {
		final String threshold = getParameterValue(THRESHOLD, String.class);
		if (threshold == null) {
			LOG.debug("threshold is null");
			return;
		}
		try {
			prefs.getFilterSettings().setMinPriority(threshold);
		} catch (final Exception ex) {
			LOG.error(
			        "{}: could not set <threshold>, reason {}, leaving it alone",
			        LOG_PREFIX, threshold, ex);
		}
	}

	public void setVisitors(final UserPreferences prefs) throws CoreException {
		final String visitors = getParameterValue(VISITORS, String.class);
		if (visitors == null) {
			return;
		}
		final List<String> detectorsList =
		        Arrays.asList(StringUtils.split(visitors, ","));
		prefs.enableAllDetectors(false);
		final DetectorFactoryCollection dfc =
		        DetectorFactoryCollection.instance();
		for (final String d : detectorsList) {
			final DetectorFactory df = dfc.getFactory(d);
			if (df == null) {
				LOG.error(String.format("[%s]: IGNORING unknown detector [%s]",
				        LOG_PREFIX, d));
			} else {
				prefs.enableDetector(df, true);
			}
		}
	}

	public static MavenPluginConfigurationTranslator newInstance(
	        final IMaven maven,
	        final MavenPluginWrapper mavenPlugin, final MavenSession session,
	        final MavenProject mavenProject, final IProject project,
	        final IProgressMonitor monitor) throws CoreException {

		final List<MojoExecution> mojoExecutions =
		        mavenPlugin.getMojoExecutions();
		if (mojoExecutions.size() != 1) {
			throw new CoreException(
			        new Status(IStatus.ERROR,
			                FrameworkUtil
			                        .getBundle(
			                                MavenPluginConfigurationTranslator.class)
			                        .getSymbolicName(),
			                "Wrong number of executions. Expected 1. Found "
			                        + mojoExecutions.size()));
		}
		final MojoExecution execution = mojoExecutions.get(0);
		return new MavenPluginConfigurationTranslator(maven, session, mavenProject,
				execution, project, monitor);
	}
}
