package org.maven.ide.eclipse.extensions.project.configurators.pmd;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleReference;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetReference;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDNature;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectPropertiesManager;
import net.sourceforge.pmd.eclipse.runtime.properties.PropertiesException;
import net.sourceforge.pmd.eclipse.runtime.writer.WriterException;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.maven.ide.eclipse.extensions.shared.util.AbstractMavenPluginProjectConfigurator;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginConfigurationExtractor;
import org.maven.ide.eclipse.extensions.shared.util.MavenPluginWrapper;
import org.maven.ide.eclipse.extensions.shared.util.ResourceResolver;
import static org.maven.ide.eclipse.extensions.project.configurators.pmd.PmdEclipseConstants.*;

public class EclipsePmdProjectConfigurator
        extends AbstractMavenPluginProjectConfigurator {

    // create a rule set factory for instantiating rule sets
    private RuleSetFactory factory = new RuleSetFactory();

    @Override
    protected String getMavenPluginGroupId() {
        return MAVEN_PLUGIN_GROUPID;
    }

    @Override
    protected String getMavenPluginArtifactId() {
        return MAVEN_PLUGIN_ARTIFACTID;
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
            final MavenPluginWrapper pluginWrapper,
            final MavenPluginConfigurationExtractor mavenPluginCfg) throws CoreException {

        this.console.logMessage(String.format(
                "[%s]: Eclipse PMD Configuration STARTED", this.getLogPrefix()));
        
        final MavenPluginConfigurationTranslator pluginCfgTranslator = 
            MavenPluginConfigurationTranslator.newInstance(
                    mavenProject,
                    pluginWrapper,
                    project,
                    this.getLogPrefix());
        
        final ResourceResolver resourceResolver = ResourceResolver.newInstance(
                pluginWrapper, 
                this.getLogPrefix());
        
        this.createOrUpdateEclipsePmdConfiguration(
                project, 
                resourceResolver, 
                pluginCfgTranslator, 
                monitor);

        this.addPMDNature(project, monitor);
        this.console.logMessage(String.format(
                "[%s]: Eclipse PMD Configuration ENDED", this.getLogPrefix()));
    }

    private void addPMDNature(
            final IProject project, 
            final IProgressMonitor monitor) throws CoreException {
        PMDNature.addPMDNature(project, monitor);
        this.console.logMessage(String.format(
                "[%s]: ADDED PMD nature and builder to the eclipse project",
                this.getLogPrefix()));
    }

    @Override
    protected void unconfigureEclipsePlugin(final IProject project, final IProgressMonitor monitor) 
        throws CoreException {
        IProjectPropertiesManager mgr = PMDPlugin.getDefault().getPropertiesManager();
        try {
            IProjectProperties projectProperties = mgr.loadProjectProperties(project);
            projectProperties.setPmdEnabled(false);
            projectProperties.setRuleSetStoredInProject(false);
            mgr.storeProjectProperties(projectProperties);
        } catch (PropertiesException ex) {
            this.console.logError(String.format(
                    "[%s]: Couldn't disable PMD: [%s]", 
                    this.getLogPrefix(), ex.getLocalizedMessage()));
        }

        PMDNature.removePMDNature(project, monitor);
        this.console.logMessage(String.format(
                "[%s]: REMOVED PMD nature and builder to the eclipse project",
                this.getLogPrefix()));

        //delete .pmdruleset if any
        this.console.logMessage(String.format(
                "[%s]: Attempting to remove [%s] from project", 
                this.getLogPrefix(), PMD_RULESET_FILE));
        final IResource pmdRulesetResource = project.getFile(PMD_RULESET_FILE);
        pmdRulesetResource.delete(IResource.FORCE, monitor);
        
        //delete .pmd if any
        this.console.logMessage(String.format(
                "[%s]: Attempting to remove [%s] from project", 
                this.getLogPrefix(), PMD_SETTINGS_FILE));
        final IResource pmdPropertiesResource = project.getFile(PMD_SETTINGS_FILE);
        pmdPropertiesResource.delete(IResource.FORCE, monitor);
    }

    /**
     * Configures the PMD plugin based on the POM contents
     * @throws CoreException if the creation failed.
     */
    private void createOrUpdateEclipsePmdConfiguration(
            final IProject project,
            final ResourceResolver resourceResolver,
            final MavenPluginConfigurationTranslator pluginCfgTranslator,
            final IProgressMonitor monitor) throws CoreException {

        final RuleSet ruleset = this.createPmdRuleSet(
                pluginCfgTranslator, 
                resourceResolver);

        this.buildAndAddPmdExcludeAndIncludePatternToRuleSet(
                pluginCfgTranslator, 
                ruleset);
        
        //persist the ruleset to a file under the project.
        final File rulesetFile = writeRuleSet(
                project.getFile(PMD_RULESET_FILE), 
                ruleset, 
                monitor);

        try {
            final IProjectPropertiesManager mgr = PMDPlugin.getDefault()
                .getPropertiesManager();
            final IProjectProperties projectProperties = mgr.loadProjectProperties(project);
            projectProperties.setPmdEnabled(true);
            projectProperties.setRuleSetFile(rulesetFile.getAbsolutePath());
            projectProperties.setRuleSetStoredInProject(true);
            mgr.storeProjectProperties(projectProperties);
        } catch (PropertiesException ex) {
            this.console.logError(String.format(
                    "[%s]: Could not enable PMD in Eclipse-PMD configuration for the project",
                    this.getLogPrefix()));
            //remove the files
            this.unconfigureEclipsePlugin(project, monitor);
        }
    }
    
    private RuleSet createPmdRuleSet(
            final MavenPluginConfigurationTranslator pluginCfgTranslator,
            final ResourceResolver resourceResolver) {

        final RuleSet ruleSet = new RuleSet();
        ruleSet.setName("M2Eclipse PMD RuleSet");

        final List<String> rulesetStringLocations = 
            pluginCfgTranslator.getRulesets();
        if (rulesetStringLocations.size() > 0) {
            for (String loc : rulesetStringLocations) {
                final URL resolvedLocation =
                    resourceResolver.resolveLocation(loc);
                RuleSet ruleSetAtLocations;
                try {
                    ruleSetAtLocations = this.factory
                        .createRuleSet(resolvedLocation.openStream());
                    ruleSet.addRuleSet(ruleSetAtLocations);
                } catch (IOException ex) {
                    this.console.logError(String.format(
                        "[%s]: WARNING Ignoring rules from [%s] location",
                        this.getLogPrefix(), loc));
                }
            }
        } else {
            ruleSet.addRule(this.createRuleReference("rulesets/basic.xml"));
            ruleSet.addRule(this.createRuleReference("rulesets/unusedcode.xml"));
            ruleSet.addRule(this.createRuleReference("rulesets/imports.xml"));
        }
        
        return ruleSet;
    }

    /**
     * Serializes the ruleset for configuring eclipse PMD plugin.
     * 
     * @param rulesetFile the ruleset File resource.
     * @param ruleSet     the {@code RuleSet} instance.
     * @param monitor     the Progress monitor instance.
     * @return            the {@code File} instance of the ruleset file.
     * @throws CoreException 
     */
    private File writeRuleSet(
            final IFile rulesetFile, 
            final RuleSet ruleSet, 
            final IProgressMonitor monitor) throws CoreException {
        final PMDPlugin pmdPlugin = PMDPlugin.getDefault();

        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(
                    rulesetFile.getLocation().toFile()));
            pmdPlugin.getRuleSetWriter().write(bos, ruleSet);
            rulesetFile.refreshLocal(IResource.DEPTH_ZERO, monitor);
        } catch (IOException ex) {
            this.console.logError(String.format(
                    "[%s]: could not write ruleset, reason [%s]",
                    this.getLogPrefix(),
                    ex.getMessage()));
        } catch (WriterException ex) {
            this.console.logError(String.format(
                    "[%s]: could not write ruleset, reason [%s]",
                    this.getLogPrefix(),
                    ex.getMessage()));
        }
        return rulesetFile.getLocation().toFile();
    }


    private Rule createRuleReference(String ruleSetFileName) {
        RuleSetReference ruleSetReference = new RuleSetReference();
        ruleSetReference.setRuleSetFileName(ruleSetFileName);
        ruleSetReference.setAllRules(true);

        RuleReference ref = new RuleReference();
        ref.setRuleSetReference(ruleSetReference);

        return ref;
    }

    private void buildAndAddPmdExcludeAndIncludePatternToRuleSet(
            final MavenPluginConfigurationTranslator pluginCfgTranslator,
            final RuleSet ruleset) {
        final List<String> excludeRoots = pluginCfgTranslator.getExcludeRoots();
        final List<String> includeRoots = pluginCfgTranslator.getIncludeRoots();
        final List<String> includePatterns = pluginCfgTranslator.getIncludes();
        
        //1. check to see if any includes are specified. If they are then
        //   to line up with the behavior of maven-pmd-plugin, excludes
        //   don't make any sense at all or more specifically it is (ignored).
        final boolean includesSpecified = includePatterns.size() > 0;
        final Set<String> excludeRootsSet = new HashSet<String>();
        excludeRootsSet.addAll(excludeRoots);
        
        if (includesSpecified) {
            // Add all includeRoots to excludeRoots.
            // Add all includeRoots too..
            excludeRootsSet.addAll(includeRoots);
            this.console.logMessage(String.format(
                "[%s]: <includes> was configured, to keep with maven-pmd-plugin behavior"
               +" all <excludes> if given will be IGNORED", this.getLogPrefix()));
        } else {
            final List<String> excludePatterns = pluginCfgTranslator.getExcludes();
            // 2.) As per spec. add excludes pattern to all *includeRoots*.
            for (String ir : includeRoots) {
                for (String ep : excludePatterns) {
                    String fullPattern = ".*" + ir + ep;
                    ruleset.addExcludePattern(
                            StringUtils.replace(fullPattern, ".*.*", ".*"));
                }
            }
        }
        // 1.) Do the excludeRoots first
        for (String er : excludeRootsSet) {
            ruleset.addExcludePattern(".*" + er);
        }
        // 3.) Now all includes
        for (String ir : includeRoots) {
            for (String ip : includePatterns) {
                String fullPattern = ".*" + ir + ip;
                ruleset.addIncludePattern(
                        StringUtils.replace(fullPattern, ".*.*", ".*"));
            }
        }
    }


}
