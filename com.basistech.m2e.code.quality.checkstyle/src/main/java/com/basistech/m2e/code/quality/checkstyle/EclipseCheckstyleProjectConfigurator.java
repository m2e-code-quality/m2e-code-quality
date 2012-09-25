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
import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.MAVEN_PLUGIN_ARTIFACTID;
import static com.basistech.m2e.code.quality.checkstyle.CheckstyleEclipseConstants.MAVEN_PLUGIN_GROUPID;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.sf.eclipsecs.core.config.CheckConfigurationWorkingCopy;
import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.config.ICheckConfigurationWorkingSet;
import net.sf.eclipsecs.core.config.ResolvableProperty;
import net.sf.eclipsecs.core.config.configtypes.ConfigurationTypes;
import net.sf.eclipsecs.core.config.configtypes.IConfigurationType;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationFactory;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationWorkingCopy;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

import org.apache.maven.execution.MavenSession;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;

/**
 */
public class EclipseCheckstyleProjectConfigurator 
    extends AbstractMavenPluginProjectConfigurator {

	private final IConfigurationType remoteConfigurationType = ConfigurationTypes
            .getByInternalName("remote");
    
    public EclipseCheckstyleProjectConfigurator() {
        super();
    }

    @Override
    protected String getMavenPluginArtifactId() {
        return MAVEN_PLUGIN_ARTIFACTID;
    }

    @Override
    protected String getMavenPluginGroupId() {
        return MAVEN_PLUGIN_GROUPID;
    }
    
    @Override
	protected String[] getMavenPluginGoal() {
		return new String [] { "checkstyle", "check" };
	}

    @Override
    protected void handleProjectConfigurationChange(
    		final MavenSession session,
            final IMavenProjectFacade mavenProjectFacade,
            final IProject project,
            final IProgressMonitor monitor,
            final MavenPluginWrapper mavenPluginWrapper) throws CoreException {
    	
        final MavenPluginConfigurationTranslator mavenCheckstyleConfig = 
            MavenPluginConfigurationTranslator
                .newInstance(this,
                		     session,
                		     mavenProjectFacade.getMavenProject(monitor), 
                             mavenPluginWrapper, 
                             project);

        try {
        	final EclipseCheckstyleConfigManager csPluginNature =
        			EclipseCheckstyleConfigManager.newInstance(project);

        	if (mavenCheckstyleConfig.isActive()) {
                this.buildCheckstyleConfiguration(
                    project, 
                    mavenCheckstyleConfig);
                // Add the builder and nature
                csPluginNature.configure(monitor);
            } else {
                csPluginNature.deconfigure(monitor);
            }
        } catch (CheckstylePluginException ex) {
            //MavenLogger.log("CheckstylePluginException", ex);
        }
    }
    
    @Override
    protected void unconfigureEclipsePlugin(final IProject project, final IProgressMonitor monitor) 
        throws CoreException {

        final EclipseCheckstyleConfigManager csPluginNature =
            EclipseCheckstyleConfigManager.newInstance(project);
        csPluginNature.deconfigure(monitor);
        
    }

    private void buildCheckstyleConfiguration( 
        final IProject project,
        final MavenPluginConfigurationTranslator cfgTranslator) 
        throws CheckstylePluginException, CoreException {
        //get the ruleset from configLocation
        final URL ruleset = cfgTranslator.getRuleset();
        //construct a new working copy
        final ProjectConfigurationWorkingCopy pcWorkingCopy = 
            new ProjectConfigurationWorkingCopy(ProjectConfigurationFactory
                    .getConfiguration(project));
        pcWorkingCopy.setUseSimpleConfig(false);
        pcWorkingCopy.setSyncFormatter(Activator.getDefault().getPreferenceStore().getBoolean(CheckstyleEclipseConstants.ECLIPSE_CS_GENERATE_FORMATTER_SETTINGS));
        //build or get the checkconfig
        final ICheckConfiguration checkCfg = 
            this.createOrGetCheckstyleConfig(pcWorkingCopy, ruleset);
        if (checkCfg == null) {
            throw new CheckstylePluginException(String.format(
                    "Failed to construct CheckConfig,SKIPPING checkstyle configuration"));
        }
        //update filesets (include and exclude patterns)
        cfgTranslator.updateCheckConfigWithIncludeExcludePatterns(pcWorkingCopy, checkCfg);
        /**
         * 2. Load all properties
         */
        //get Properties from propertiesLocation
        final Properties props = cfgTranslator.getConfiguredProperties();
        cfgTranslator.updatePropertiesWithPropertyExpansion(props);
        //add the header file location to the props.
        String headerFile = cfgTranslator.getHeaderFile();
        if (headerFile != null) {
            props.setProperty("checkstyle.header.file", headerFile);
        }
        //add the cache file location to the props.
        props.setProperty("checkstyle.cache.file", ECLIPSE_CS_CACHE_FILENAME);
        //Load all properties in the checkConfig
        final List<ResolvableProperty> csProps = checkCfg.getResolvableProperties();
        csProps.clear();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            csProps.add(new ResolvableProperty((String) entry.getKey(), (String) entry.getValue()));
        }

        /**
         * 3. persist the checkconfig
         */
        if (pcWorkingCopy.isDirty()) {
            pcWorkingCopy.store();
        }
    }
        
    /**
     * Retrieve a pre-existing LocalCheckConfiguration for maven to eclipse-cs
     * integration, or create a new one
     */
    private ICheckConfiguration createOrGetCheckstyleConfig(
            final ProjectConfigurationWorkingCopy pcWorkingCopy, 
            final URL ruleset)
        throws CheckstylePluginException {
        
        final ICheckConfigurationWorkingSet workingSet = pcWorkingCopy
            .getLocalCheckConfigWorkingSet();

        CheckConfigurationWorkingCopy workingCopy = null;

        // Try to retrieve an existing checkstyle configuration to be updated
        CheckConfigurationWorkingCopy[] workingCopies = workingSet.getWorkingCopies();
        if (workingCopies != null) {
            for (CheckConfigurationWorkingCopy copy : workingCopies) {
                if (ECLIPSE_CS_PREFS_CONFIG_NAME.equals(copy.getName())) {
                    if (this.remoteConfigurationType.equals(copy.getType())) {
                        workingCopy = copy;
                        break;
                    }
                    throw new CheckstylePluginException(String.format(
                            "A local Checkstyle configuration allready exists with name "
                            + " [%s] with incompatible type [%s]",
                            ECLIPSE_CS_PREFS_CONFIG_NAME, copy.getType()));
                }
            }
        }
        //Nothing exist create a brand new one.
        if (workingCopy == null) {
            // Create a fresh check config
            workingCopy = workingSet.newWorkingCopy(this.remoteConfigurationType);
            workingCopy.setName(ECLIPSE_CS_PREFS_CONFIG_NAME);
            workingSet.addCheckConfiguration(workingCopy);
        }

        workingCopy.setDescription("maven-checkstyle-plugin configuration");
        workingCopy.setLocation(ruleset.toExternalForm());
        return workingCopy;
    }
    
    
}
