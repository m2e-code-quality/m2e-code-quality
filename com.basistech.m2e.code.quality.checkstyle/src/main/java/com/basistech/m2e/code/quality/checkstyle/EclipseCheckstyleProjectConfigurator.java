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
import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_PREFS_CONFIG_NAME;
import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_PREFS_FILE;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

import net.sf.eclipsecs.core.config.CheckConfigurationWorkingCopy;
import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.config.ICheckConfigurationWorkingSet;
import net.sf.eclipsecs.core.config.ResolvableProperty;
import net.sf.eclipsecs.core.config.configtypes.ConfigurationTypes;
import net.sf.eclipsecs.core.config.configtypes.IConfigurationType;
import net.sf.eclipsecs.core.nature.CheckstyleNature;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationFactory;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationWorkingCopy;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

/**
 */
public class EclipseCheckstyleProjectConfigurator
        extends AbstractMavenPluginProjectConfigurator {

	private static final Logger LOG =
	        LoggerFactory.getLogger(EclipseCheckstyleProjectConfigurator.class);

	private static final IConfigurationType REMOTE_CONFIGURATION_TYPE =
	        ConfigurationTypes.getByInternalName("remote");

	public EclipseCheckstyleProjectConfigurator() {
		super();
	}

	@Override
	protected String getMavenPluginArtifactId() {
		return "maven-checkstyle-plugin";
	}

	@Override
	protected String getMavenPluginGroupId() {
		return "org.apache.maven.plugins";
	}

	@Override
	protected String[] getMavenPluginGoals() {
		return new String[] {"checkstyle", "check"};
	}

	@Override
	protected void handleProjectConfigurationChange(
	        final IMavenProjectFacade mavenProjectFacade,
	        final IProject project, final IProgressMonitor monitor,
	        final MavenPluginWrapper mavenPluginWrapper,
	        final MavenSession session) throws CoreException {

		final List<MavenPluginConfigurationTranslator> mavenCheckstyleConfigs =
		        MavenPluginConfigurationTranslator.newInstance(maven, this,
		                mavenProjectFacade.getMavenProject(monitor),
		                mavenPluginWrapper, project, monitor, session);

		try {
			// construct a new working copy
			final ProjectConfigurationWorkingCopy pcWorkingCopy =
			        new ProjectConfigurationWorkingCopy(
			                ProjectConfigurationFactory
			                        .getConfiguration(project));
			pcWorkingCopy.setUseSimpleConfig(false);
			pcWorkingCopy.setSyncFormatter(
			        Activator.getDefault().getPreferenceStore().getBoolean(
			                CheckstyleEclipseConstants.ECLIPSE_CS_GENERATE_FORMATTER_SETTINGS));
			pcWorkingCopy.getFileSets().clear();

			for (final MavenPluginConfigurationTranslator mavenCheckstyleConfig : mavenCheckstyleConfigs) {
				if (!mavenCheckstyleConfig.isSkip()) {
					this.buildCheckstyleConfiguration(pcWorkingCopy,
					        mavenCheckstyleConfig);
					addNature(project, CheckstyleNature.NATURE_ID, monitor);
				} else {
					deleteEclipseFiles(project, monitor);
					removeNature(project, CheckstyleNature.NATURE_ID, monitor);
				}
			}

			// persist the checkconfig
			if (pcWorkingCopy.isDirty()) {
				pcWorkingCopy.store();
			}

		} catch (final CheckstylePluginException ex) {
			LOG.error("CheckstylePluginException", ex);
		}
	}

	private void deleteEclipseFiles(final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		final IResource checkstyleFile = project.getFile(ECLIPSE_CS_PREFS_FILE);
		checkstyleFile.delete(IResource.FORCE, monitor);
		final IResource checkstyleCacheFileResource =
		        project.getFile(ECLIPSE_CS_CACHE_FILENAME);
		checkstyleCacheFileResource.delete(IResource.FORCE, monitor);
	}

	@Override
	protected void unconfigureEclipsePlugin(final IProject project,
	        final IProgressMonitor monitor) throws CoreException {
		deleteEclipseFiles(project, monitor);
		removeNature(project, CheckstyleNature.NATURE_ID, monitor);
	}

	private void buildCheckstyleConfiguration(
	        final ProjectConfigurationWorkingCopy pcWorkingCopy,
	        final MavenPluginConfigurationTranslator cfgTranslator)
	        throws CheckstylePluginException, CoreException {
		// get the ruleset from configLocation
		final URL ruleset = cfgTranslator.getRuleset();
		// build or get the checkconfig
		final ICheckConfiguration checkCfg = this.createOrGetCheckstyleConfig(
		        pcWorkingCopy, ruleset, cfgTranslator.getExecutionId());
		// update filesets (include and exclude patterns)
		cfgTranslator.updateCheckConfigWithIncludeExcludePatterns(pcWorkingCopy,
		        checkCfg);
		// 2. Load all properties
		// get Properties from propertiesLocation
		final Properties props = cfgTranslator.getConfiguredProperties();
		cfgTranslator.updatePropertiesWithPropertyExpansion(props);
		// add the header file location to the props.
		final String headerFile = cfgTranslator.getHeaderFile();
		if (headerFile != null) {
			props.setProperty("checkstyle.header.file", headerFile);
		}
		// add the suppressions file location to the props.
		final String suppressionsFile = cfgTranslator.getSuppressionsFile();
		if (suppressionsFile != null) {
			props.setProperty(cfgTranslator.getSuppressionsFileExpression(),
			        suppressionsFile);
		}
		// add the cache file location to the props.
		props.setProperty("checkstyle.cache.file", ECLIPSE_CS_CACHE_FILENAME);
		// Load all properties in the checkConfig
		final List<ResolvableProperty> csProps =
		        checkCfg.getResolvableProperties();
		csProps.clear();
		for (final Map.Entry<Object, Object> entry : props.entrySet()) {
			csProps.add(new ResolvableProperty((String) entry.getKey(),
			        (String) entry.getValue()));
		}

	}

	/**
	 * Retrieve a pre-existing LocalCheckConfiguration for maven to eclipse-cs
	 * integration, or create a new one
	 */
	private ICheckConfiguration createOrGetCheckstyleConfig(
	        final ProjectConfigurationWorkingCopy pcWorkingCopy,
	        final URL ruleset, final String executionId)
	        throws CheckstylePluginException {
		final String configName =
		        ECLIPSE_CS_PREFS_CONFIG_NAME + " " + executionId;
		final ICheckConfigurationWorkingSet workingSet =
		        pcWorkingCopy.getLocalCheckConfigWorkingSet();

		CheckConfigurationWorkingCopy workingCopy = null;

		// Try to retrieve an existing checkstyle configuration to be updated
		CheckConfigurationWorkingCopy[] workingCopies =
		        workingSet.getWorkingCopies();
		if (workingCopies == null) {
			LOG.error("The working copies are null");
			workingCopies = new CheckConfigurationWorkingCopy[0];
		}
		for (final CheckConfigurationWorkingCopy copy : workingCopies) {
			if (configName.equals(copy.getName())) {
				if (REMOTE_CONFIGURATION_TYPE.equals(copy.getType())) {
					workingCopy = copy;
					break;
				}
				throw new CheckstylePluginException(String.format(
				        "A local Checkstyle configuration already exists with name "
				                + " [%s] with incompatible type [%s]",
				        configName, copy.getType()));
			}
		}
		// Nothing exist create a brand new one.
		if (workingCopy == null) {
			// Create a fresh check config
			workingCopy = workingSet.newWorkingCopy(REMOTE_CONFIGURATION_TYPE);
			workingCopy.setName(configName);
			workingSet.addCheckConfiguration(workingCopy);
		}

		workingCopy.setDescription(
		        "maven-checkstyle-plugin configuration " + executionId);
		workingCopy.setLocation(ruleset.toExternalForm());
		return workingCopy;
	}

}
