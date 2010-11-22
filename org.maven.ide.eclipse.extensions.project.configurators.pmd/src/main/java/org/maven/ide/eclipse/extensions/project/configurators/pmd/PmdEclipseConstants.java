package org.maven.ide.eclipse.extensions.project.configurators.pmd;


/**
 * Constants as it relates to eclipse PMD plugin
 */
public final class PmdEclipseConstants {
   
    public static final String LOG_PREFIX = "M2E-PMD";
    public static final String MAVEN_PLUGIN_GROUPID = "org.apache.maven.plugins";
    public static final String MAVEN_PLUGIN_ARTIFACTID = "maven-pmd-plugin";

    public static final String PMD_RULESET_FILE = ".pmdruleset";
    public static final String PMD_SETTINGS_FILE = ".pmd";
   
    private PmdEclipseConstants() {
        //no instantiation.
    }
    
}
