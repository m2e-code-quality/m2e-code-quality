package org.maven.ide.eclipse.extensions.project.configurators.checkstyle;

import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_CACHE_FILENAME;
import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.ECLIPSE_CS_PREFS_CONFIG_NAME;
import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.LOG_PREFIX;
import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.MAVEN_PLUGIN_ARTIFACTID;
import static org.maven.ide.eclipse.extensions.project.configurators.checkstyle.CheckstyleEclipseConstants.MAVEN_PLUGIN_GROUPID;

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

import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.core.MavenLogger;
import org.maven.ide.eclipse.extensions.shared.util.AbstractMavenPluginProjectConfigurator;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;

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
    protected String getLogPrefix() {
        return LOG_PREFIX;
    }

    @Override
    protected void handleProjectConfigurationChange(
            final MavenProject mavenProject,
            final IProject project,
            final IProgressMonitor monitor,
            final MavenPluginWrapper mavenPluginWrapper,
            final MavenPluginConfigurationExtractor mavenPluginCfg) throws CoreException {

        this.console.logMessage(String.format(
                "[%s]: Eclipse Checkstyle Configuration started", LOG_PREFIX));
        
        final MavenPluginConfigurationTranslator mavenCheckstyleConfig = 
            MavenPluginConfigurationTranslator
                .newInstance(mavenProject, 
                             mavenPluginWrapper, 
                             project, 
                             LOG_PREFIX);

        try {
            this.buildCheckstyleConfiguration(
                    project, 
                    mavenCheckstyleConfig);
            final EclipseCheckstyleConfigManager csPluginNature =
                EclipseCheckstyleConfigManager.newInstance(project);
            // Add the builder and nature
            csPluginNature.configure(monitor);
        } catch (CheckstylePluginException ex) {
            this.console.logError(String.format("[%s]: %s", LOG_PREFIX, ex));
            MavenLogger.log("CheckstylePluginException", ex);
        }
        this.console.logMessage(String.format(
                "[%s]: Eclipse Checkstyle Configuration ended", LOG_PREFIX));
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
        throws CheckstylePluginException {
        //get the ruleset from configLocation
        final URL ruleset = cfgTranslator.getRuleset();
        //construct a new working copy
        final ProjectConfigurationWorkingCopy pcWorkingCopy = 
            new ProjectConfigurationWorkingCopy(ProjectConfigurationFactory
                    .getConfiguration(project));
        pcWorkingCopy.setUseSimpleConfig(false);
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
                        this.console.logMessage(String.format(
                          "[%s]: A local Checkstyle configuration allready exists with name "
                          + " [%s]. Will update with maven-checkstyle-plugin configuration for project",
                          LOG_PREFIX, ECLIPSE_CS_PREFS_CONFIG_NAME));
                        workingCopy = copy;
                        break;
                    }
                    throw new CheckstylePluginException(String.format(
                            "[%s]: A local Checkstyle configuration allready exists with name "
                            + " [%s] with incompatible type [%s]",
                            LOG_PREFIX, ECLIPSE_CS_PREFS_CONFIG_NAME, copy.getType()));
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
