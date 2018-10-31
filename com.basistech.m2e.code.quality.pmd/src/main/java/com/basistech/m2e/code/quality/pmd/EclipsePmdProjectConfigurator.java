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
package com.basistech.m2e.code.quality.pmd;

import static com.basistech.m2e.code.quality.pmd.PmdEclipseConstants.MAVEN_PLUGIN_ARTIFACTID;
import static com.basistech.m2e.code.quality.pmd.PmdEclipseConstants.MAVEN_PLUGIN_GROUPID;
import static com.basistech.m2e.code.quality.pmd.PmdEclipseConstants.PMD_RULESET_FILE;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;

import net.sourceforge.pmd.PMDException;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetNotFoundException;
import net.sourceforge.pmd.RuleSetReferenceId;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.builder.MarkerUtil;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDNature;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectPropertiesManager;
import net.sourceforge.pmd.eclipse.runtime.properties.PropertiesException;
import net.sourceforge.pmd.eclipse.runtime.writer.WriterException;
import net.sourceforge.pmd.util.ResourceLoader;

public class EclipsePmdProjectConfigurator
        extends AbstractMavenPluginProjectConfigurator<PMDNature> {

	private static final Logger LOG =
	        LoggerFactory.getLogger(EclipsePmdProjectConfigurator.class);

	// create a rule set factory for instantiating rule sets
	private final RuleSetFactory factory = new RuleSetFactory();

	public EclipsePmdProjectConfigurator() {
		super(PMDNature.PMD_NATURE, PMDRuntimeConstants.PMD_MARKER, PMD_RULESET_FILE);
	}

	@Override
	protected String getMavenPluginGroupId() {
		return MAVEN_PLUGIN_GROUPID;
	}

	@Override
	protected String getMavenPluginArtifactId() {
		return MAVEN_PLUGIN_ARTIFACTID;
	}

	@Override
	protected String[] getMavenPluginGoals() {
		return new String[] {"check"};
	}

	@Override
	protected void handleProjectConfigurationChange(
	        final IMavenProjectFacade mavenProjectFacade,
	        final IProject project, final MavenPluginWrapper mavenPluginWrapper,
	        final MavenSession session, final IProgressMonitor monitor) throws CoreException {

		final MojoExecution execution = findMojoExecution(mavenPluginWrapper);
		final MojoExecution pmdGoalExecution = findForkedExecution(execution,
		        "org.apache.maven.plugins", "maven-pmd-plugin", "pmd");
		final MavenPluginConfigurationTranslator pluginCfgTranslator =
		        MavenPluginConfigurationTranslator.newInstance(maven,
		                session, mavenProjectFacade.getMavenProject(monitor),
		                execution, pmdGoalExecution, project, monitor);
		this.createOrUpdateEclipsePmdConfiguration(mavenPluginWrapper, project,
		        pluginCfgTranslator, monitor, session);

		// in PMD we need to enable or disable the builder for skip
		if (!this.createOrUpdateEclipsePmdConfiguration(mavenPluginWrapper, project,
		        pluginCfgTranslator, monitor, session)) {
			unconfigureEclipsePlugin(project, monitor);
			return;
		}

		configure(project, pluginCfgTranslator.isSkip(), monitor);
	}

	@Override
	protected void unconfigureEclipsePlugin(IProject project, IProgressMonitor monitor) throws CoreException {
		final IProjectPropertiesManager mgr =
		        PMDPlugin.getDefault().getPropertiesManager();
		try {
			final IProjectProperties projectProperties =
			        mgr.loadProjectProperties(project);
			projectProperties.setPmdEnabled(false);
			projectProperties.setRuleSetStoredInProject(false);
			mgr.storeProjectProperties(projectProperties);
		} catch (final PropertiesException ex) {
		}
		super.unconfigureEclipsePlugin(project, monitor);
	}

	/**
	 * Configures the PMD plugin based on the POM contents
	 * 
	 * @throws CoreException
	 *             if the creation failed.
	 */
	private boolean createOrUpdateEclipsePmdConfiguration(
	        final MavenPluginWrapper pluginWrapper, final IProject project,
	        final MavenPluginConfigurationTranslator pluginCfgTranslator,
	        final IProgressMonitor monitor, final MavenSession session)
	        throws CoreException {

		final MojoExecution execution = findMojoExecution(pluginWrapper);
		final ResourceResolver resourceResolver = AbstractMavenPluginProjectConfigurator
		        .getResourceResolver(execution, session, project.getLocation());
		try {
			List<Rule> allRules = this.locatePmdRules(pluginCfgTranslator, resourceResolver);
			Collection<String> excludePatterns = new ArrayList<>();
			Collection<String> includePatterns = new ArrayList<>();

			this.buildAndAddPmdExcludeAndIncludePatterns(
			        pluginCfgTranslator, excludePatterns, includePatterns);

			final RuleSet ruleset = this.factory.createNewRuleSet("M2Eclipse PMD RuleSet", "M2Eclipse PMD RuleSet",
					PMD_RULESET_FILE, excludePatterns, includePatterns, allRules);

			// persist the ruleset to a file under the project.
			final File rulesetFile = writeRuleSet(
			        project.getFile(PMD_RULESET_FILE), ruleset, monitor);

			try {
				final IProjectPropertiesManager mgr =
				        PMDPlugin.getDefault().getPropertiesManager();
				final IProjectProperties projectProperties =
				        mgr.loadProjectProperties(project);
				projectProperties.setPmdEnabled(true);
				projectProperties.setRuleSetFile(rulesetFile.getAbsolutePath());
				projectProperties.setRuleSetStoredInProject(true);
				mgr.storeProjectProperties(projectProperties);
			} catch (final PropertiesException ex) {
				// remove the files
				return false;
			}
		} catch (final PMDException ex) {
			// nothing to do, skip configuration
		}
		return true;
	}

	private List<Rule> locatePmdRules(
	        final MavenPluginConfigurationTranslator pluginCfgTranslator,
	        final ResourceResolver resourceResolver)
	        throws CoreException, PMDException {

		List<Rule> allRules = new ArrayList<>();

		final List<String> rulesetStringLocations =
		        pluginCfgTranslator.getRulesets();

		for (final String loc : rulesetStringLocations) {
			final RuleSetReferenceId ruleSetReferenceId =
			        new RuleSetReferenceId(loc);
			final URL resolvedLocation = resourceResolver
			        .resolveLocation(ruleSetReferenceId.getRuleSetFileName());

			if (resolvedLocation == null) {
				throw new PMDException(String.format(
				        "Failed to resolve RuleSet from location [%s],SKIPPING Eclipse PMD configuration",
				        loc));
			}

			RuleSet ruleSetAtLocations;
			try {
				final RuleSetReferenceId resolvedRuleSetReference =
				        new RuleSetReferenceId(loc) {

					        @Override
					        public InputStream getInputStream(
				                    final ResourceLoader arg0)
				                    throws RuleSetNotFoundException {
						        try {
							        return resolvedLocation.openStream();
						        } catch (final IOException e) {
							        // ignore them.
						        }
						        LOG.warn("No ruleset found for {}", loc);
						        return null;
					        }
				        };
				ruleSetAtLocations =
				        this.factory.createRuleSet(resolvedRuleSetReference);
				allRules.addAll(ruleSetAtLocations.getRules());
			} catch (final RuleSetNotFoundException e) {
				LOG.error("Couldn't find ruleset {}", loc, e);
			}
		}

		return allRules;
	}

	/**
	 * Serializes the ruleset for configuring eclipse PMD plugin.
	 *
	 * @param rulesetFile
	 *            the ruleset File resource.
	 * @param ruleSet
	 *            the {@code RuleSet} instance.
	 * @param monitor
	 *            the Progress monitor instance.
	 * @return the {@code File} instance of the ruleset file.
	 * @throws CoreException
	 */
	private File writeRuleSet(final IFile rulesetFile, final RuleSet ruleSet,
	        final IProgressMonitor monitor) throws CoreException {
		final PMDPlugin pmdPlugin = PMDPlugin.getDefault();

		final ByteArrayOutputStream byteArrayStream =
		        new ByteArrayOutputStream();
		try (BufferedOutputStream outputStream = new BufferedOutputStream(
		        new FileOutputStream(rulesetFile.getLocation().toFile()));) {

			pmdPlugin.getRuleSetWriter().write(byteArrayStream, ruleSet);

			// ..and now we have two problems
			final String fixedXml = byteArrayStream.toString("UTF-8")
			        .replaceAll("\\<exclude\\>(.*)\\</exclude\\>",
			                "<exclude name=\"$1\"/>");

			outputStream.write(fixedXml.getBytes(Charset.forName("UTF-8")));

			rulesetFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
		} catch (IOException | WriterException ex) {
			//
		}
		return rulesetFile.getLocation().toFile();
	}

	private void buildAndAddPmdExcludeAndIncludePatterns(
	        final MavenPluginConfigurationTranslator pluginCfgTranslator,
	        final Collection<String> excludePatterns, final Collection<String> includePatterns) {
		final List<String> excludeRoots = pluginCfgTranslator.getExcludeRoots();
		final List<String> includeRoots = pluginCfgTranslator.getIncludeRoots();
		final List<String> pluginIncludes = pluginCfgTranslator.getIncludes();

		// 1. check to see if any includes are specified. If they are then
		// to line up with the behavior of maven-pmd-plugin, excludes
		// don't make any sense at all or more specifically it is (ignored).
		final boolean includesSpecified = !includePatterns.isEmpty();
		final Set<String> excludeRootsSet = new HashSet<>();
		excludeRootsSet.addAll(excludeRoots);

		if (includesSpecified) {
			// Add all includeRoots to excludeRoots.
			// Add all includeRoots too..
			excludeRootsSet.addAll(includeRoots);
		} else {
			final List<String> pluginExcludes =
			        pluginCfgTranslator.getExcludes();
			// 2.) As per spec. add excludes pattern to all *includeRoots*.
			for (final String ir : includeRoots) {
				for (final String ep : pluginExcludes) {
					final String fullPattern = ".*" + ir + ep;
					excludePatterns.add(
					        StringUtils.replace(fullPattern, ".*.*", ".*"));
				}
			}
		}
		// 1.) Do the excludeRoots first
		for (final String er : excludeRootsSet) {
			excludePatterns.add(".*" + er);
		}
		// 3.) Now all includes
		for (final String ir : includeRoots) {
			for (final String ip : pluginIncludes) {
				final String fullPattern = ".*" + ir + ip;
				includePatterns.add(
				        StringUtils.replace(fullPattern, ".*.*", ".*"));
			}
		}
	}

	private MojoExecution findMojoExecution(
	        final MavenPluginWrapper mavenPluginWrapper) throws CoreException {
		final List<MojoExecution> mojoExecutions =
		        mavenPluginWrapper.getMojoExecutions();
		if (mojoExecutions.size() != 1) {
			throw new CoreException(
			        new Status(IStatus.ERROR, PmdEclipseConstants.PLUGIN_ID,
			                "Wrong number of executions. Expected 1. Found "
			                        + mojoExecutions.size()));
		}
		final MojoExecution execution = mojoExecutions.get(0);
		return execution;
	}

	@Override
	protected void removeNature(IProject project, IProgressMonitor monitor) throws CoreException {
		super.removeNature(project, monitor);

		// clean all PMD markers
		MarkerUtil.deleteAllMarkersIn(project);
	}
}
