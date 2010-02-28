package org.maven.ide.eclipse.extensions.project.configurators.checkstyle;


/**
 * Constants as it relates to eclipse checkstyle plugin
 */
public final class CheckstyleEclipseConstants {
   
    public static final String LOG_PREFIX = "M2E-CS";
    public static final String MAVEN_PLUGIN_GROUPID = "org.apache.maven.plugins";
    public static final String MAVEN_PLUGIN_ARTIFACTID = "maven-checkstyle-plugin";
    public static final String ECLIPSE_CS_PREFS_FILE = ".checkstyle";
    public static final String ECLIPSE_CS_PREFS_CONFIG_NAME = "maven-chekstyle-plugin";
    public static final String ECLIPSE_CS_CACHE_FILENAME = "${project_loc}/checkstyle-cachefile";
    
    private CheckstyleEclipseConstants() {
        //no instantiation.
    }
    
}
