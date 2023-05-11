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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
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

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RulePriority;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetLoadException;
import net.sourceforge.pmd.RuleSetLoader;
import net.sourceforge.pmd.RuleSetReferenceId;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.builder.MarkerUtil;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDNature;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectPropertiesManager;
import net.sourceforge.pmd.eclipse.runtime.properties.PropertiesException;
import net.sourceforge.pmd.eclipse.runtime.writer.WriterException;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;

public class EclipsePmdProjectConfigurator extends AbstractMavenPluginProjectConfigurator<PMDNature> {
	private static final Pattern XML_ENCODING_PATTERN = Pattern.compile("<\\?xml\\s+version\\s*=\\s*['\"][^'\"]+['\"]\\s+encoding\\s*=\\s*['\"]([^'\"]+)['\"]");
	private static final byte[] UTF16BE_BOM = new byte[] { (byte) 0xfe, (byte) 0xff, 0x00, 0x3c };
	private static final byte[] UTF16LE_BOM = new byte[] { (byte) 0xff, (byte) 0xfe, 0x3c, 0x00 };
	private static final byte[] UTF8_BOM = new byte[] { (byte) 0xef, (byte) 0xbb, (byte) 0xbf, 0x3c };
	private static final byte[] UTF16BE = new byte[] { 0x00, 0x3c, 0x00, 0x3f };
	private static final byte[] UTF16LE = new byte[] { 0x3c, 0x00, 0x3f, 0x00 };
	private static final byte[] ASCII = new byte[] { 0x3c, 0x3f, 0x78, 0x6d };

	private static final Logger LOG = LoggerFactory.getLogger(EclipsePmdProjectConfigurator.class);

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
		return new String[] { "check" };
	}

	@Override
	protected void handleProjectConfigurationChange(final IMavenProjectFacade mavenProjectFacade,
			final IProject project, final MavenPluginWrapper mavenPluginWrapper, final IProgressMonitor monitor)
			throws CoreException {

		final MavenProject mavenProject = mavenProjectFacade.getMavenProject();
		final MojoExecution execution = findMojoExecution(mavenPluginWrapper);
		final MojoExecution pmdGoalExecution = findForkedExecution(execution, "org.apache.maven.plugins",
				"maven-pmd-plugin", "pmd");
		final MavenPluginConfigurationTranslator pluginCfgTranslator = MavenPluginConfigurationTranslator.newInstance(
				maven, mavenProjectFacade.getMavenProject(monitor), execution, pmdGoalExecution, project, monitor);
		this.createOrUpdateEclipsePmdConfiguration(mavenPluginWrapper, project, pluginCfgTranslator, monitor,
				mavenProject);

		// in PMD we need to enable or disable the builder for skip
		if (!this.createOrUpdateEclipsePmdConfiguration(mavenPluginWrapper, project, pluginCfgTranslator, monitor,
				mavenProject)) {
			unconfigureEclipsePlugin(project, monitor);
			return;
		}

		configure(project, pluginCfgTranslator.isSkip(), monitor);
	}

	@Override
	protected void unconfigureEclipsePlugin(IProject project, IProgressMonitor monitor) throws CoreException {
		final IProjectPropertiesManager mgr = PMDPlugin.getDefault().getPropertiesManager();
		try {
			final IProjectProperties projectProperties = mgr.loadProjectProperties(project);
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
	 * @throws CoreException if the creation failed.
	 */
	private boolean createOrUpdateEclipsePmdConfiguration(final MavenPluginWrapper pluginWrapper,
			final IProject project, final MavenPluginConfigurationTranslator pluginCfgTranslator,
			final IProgressMonitor monitor, final MavenProject mavenProject) throws CoreException {

		final ResourceResolver resourceResolver = pluginCfgTranslator.getResourceResolver();
		List<Rule> allRules = this.locatePmdRules(pluginCfgTranslator, resourceResolver);
		Collection<Pattern> excludePatterns = new ArrayList<>();
		Collection<Pattern> includePatterns = new ArrayList<>();

		this.buildAndAddPmdExcludeAndIncludePatterns(pluginCfgTranslator, excludePatterns, includePatterns);

		final RuleSet ruleset = RuleSet.create("M2Eclipse PMD RuleSet", "M2Eclipse PMD RuleSet",
				PMD_RULESET_FILE, excludePatterns, includePatterns, allRules);

		// persist the ruleset to a file under the project.
		final File rulesetFile = writeRuleSet(project.getFile(PMD_RULESET_FILE), ruleset, monitor);

		try {
			final IProjectPropertiesManager mgr = PMDPlugin.getDefault().getPropertiesManager();
			final IProjectProperties projectProperties = mgr.loadProjectProperties(project);
			projectProperties.setPmdEnabled(true);
			projectProperties.setRuleSetFile(rulesetFile.getAbsolutePath());
			projectProperties.setRuleSetStoredInProject(true);
			mgr.storeProjectProperties(projectProperties);
		} catch (final PropertiesException ex) {
			// remove the files
			return false;
		}
		return true;
	}

	private List<Rule> locatePmdRules(final MavenPluginConfigurationTranslator pluginCfgTranslator,
			final ResourceResolver resourceResolver) throws CoreException {

		PMDConfiguration pmdConfiguration = new PMDConfiguration();
		pmdConfiguration.setMinimumPriority(RulePriority.LOW);
		pmdConfiguration.setRuleSetFactoryCompatibilityEnabled(false);
		final RuleSetLoader ruleSetLoader = RuleSetLoader.fromPmdConfig(pmdConfiguration);

		List<Rule> allRules = new ArrayList<>();

		final List<String> rulesetStringLocations = pluginCfgTranslator.getRulesets();

		for (final String loc : rulesetStringLocations) {
			final RuleSetReferenceId ruleSetReferenceId = new RuleSetReferenceId(loc);
			final URL resolvedLocation = resourceResolver.resolveLocation(ruleSetReferenceId.getRuleSetFileName());

			if (resolvedLocation == null) {
				throw new CoreException(Status.error(String.format(
						"Failed to resolve RuleSet from location [%s],SKIPPING Eclipse PMD configuration", loc)));
			}

			try (InputStream in = resolvedLocation.openStream()) {
				RuleSet ruleSetAtLocations = ruleSetLoader.loadFromString(loc, loadXmlStreamIntoString(in));
				allRules.addAll(ruleSetAtLocations.getRules());
			} catch (final RuleSetLoadException e) {
				LOG.error("Couldn't load ruleset {}", loc, e);
			} catch (final IOException e) {
				LOG.error("Couldn't find ruleset {}", loc, e);
			}
		}

		return allRules;
	}

	private String loadXmlStreamIntoString(InputStream r) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BufferedInputStream in = new BufferedInputStream(r);
		in.transferTo(bytes);

		// this is an xml stream, find the encoding in the first bytes. A well-formed xml file
		// must begin with "<?xml version=" and we can guess the basic encoding (UTF-16 vs. UTF-8)
		// see https://www.w3.org/TR/xml/#sec-guessing
		byte[] byteArray = bytes.toByteArray();
		Charset charset = Charset.defaultCharset();
		if (Arrays.equals(byteArray, 0, 4, UTF16BE_BOM, 0, 4)) {
			charset = StandardCharsets.UTF_16BE;
		} else if (Arrays.equals(byteArray, 0, 4, UTF16LE_BOM, 0, 4)) {
			charset = StandardCharsets.UTF_16LE;
		} else if (Arrays.equals(byteArray, 0, 4, UTF8_BOM, 0, 4)) {
			charset = StandardCharsets.UTF_8;
		} else if (Arrays.equals(byteArray, 0, 4, UTF16BE, 0, 4)) {
			charset = StandardCharsets.UTF_16BE;
		} else if (Arrays.equals(byteArray, 0, 4, UTF16LE, 0, 4)) {
			charset = StandardCharsets.UTF_16LE;
		} else if (Arrays.equals(byteArray, 0, 4, ASCII, 0, 4)) {
			charset = StandardCharsets.ISO_8859_1;
		}
		LOG.debug("Detected {} using the first 4 bytes", charset);

		// <?xml version="1.0" encoding="UTF-8"?>
		CharBuffer decoded = charset.decode(ByteBuffer.wrap(byteArray));
		String prolog = decoded.subSequence(0, Math.min(100, decoded.length())).toString();
		Matcher matcher = XML_ENCODING_PATTERN.matcher(prolog);
		if (matcher.find()) {
			LOG.debug("Found encoding {} in XML prolog", matcher.group(1));
			charset = Charset.forName(matcher.group(1));
			decoded = charset.decode(ByteBuffer.wrap(byteArray));
		}
		return decoded.toString();
	}

	/**
	 * Serializes the ruleset for configuring eclipse PMD plugin.
	 *
	 * @param rulesetFile the ruleset File resource.
	 * @param ruleSet     the {@code RuleSet} instance.
	 * @param monitor     the Progress monitor instance.
	 * @return the {@code File} instance of the ruleset file.
	 * @throws CoreException
	 */
	private File writeRuleSet(final IFile rulesetFile, final RuleSet ruleSet, final IProgressMonitor monitor)
			throws CoreException {
		final PMDPlugin pmdPlugin = PMDPlugin.getDefault();

		final ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
		try (BufferedOutputStream outputStream = new BufferedOutputStream(
				new FileOutputStream(rulesetFile.getLocation().toFile()));) {

			pmdPlugin.getRuleSetWriter().write(byteArrayStream, ruleSet);

			// ..and now we have two problems
			final String fixedXml = byteArrayStream.toString("UTF-8").replaceAll("\\<exclude\\>(.*)\\</exclude\\>",
					"<exclude name=\"$1\"/>");

			outputStream.write(fixedXml.getBytes(Charset.forName("UTF-8")));

			rulesetFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
		} catch (IOException | WriterException ex) {
			//
		}
		return rulesetFile.getLocation().toFile();
	}

	private void buildAndAddPmdExcludeAndIncludePatterns(final MavenPluginConfigurationTranslator pluginCfgTranslator,
			final Collection<Pattern> excludePatterns, final Collection<Pattern> includePatterns) {
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
			final List<String> pluginExcludes = pluginCfgTranslator.getExcludes();
			// 2.) As per spec. add excludes pattern to all *includeRoots*.
			for (final String ir : includeRoots) {
				for (final String ep : pluginExcludes) {
					final String fullPattern = ".*" + ir + ep;
					excludePatterns.add(Pattern.compile(StringUtils.replace(fullPattern, ".*.*", ".*")));
				}
			}
		}
		// 1.) Do the excludeRoots first
		for (final String er : excludeRootsSet) {
			excludePatterns.add(Pattern.compile(".*" + er));
		}
		// 3.) Now all includes
		for (final String ir : includeRoots) {
			for (final String ip : pluginIncludes) {
				final String fullPattern = ".*" + ir + ip;
				includePatterns.add(Pattern.compile(StringUtils.replace(fullPattern, ".*.*", ".*")));
			}
		}
	}

	private MojoExecution findMojoExecution(final MavenPluginWrapper mavenPluginWrapper) throws CoreException {
		final List<MojoExecution> mojoExecutions = mavenPluginWrapper.getMojoExecutions();
		if (mojoExecutions.size() != 1) {
			throw new CoreException(new Status(IStatus.ERROR, PmdEclipseConstants.PLUGIN_ID,
					"Wrong number of executions. Expected 1. Found " + mojoExecutions.size()));
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
