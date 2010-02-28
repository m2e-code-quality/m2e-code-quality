package org.maven.ide.eclipse.extensions.project.configurators.findbugs;


/**
 * Constants as it relates to eclipse Findbugs plugin
 */
public final class FindbugsEclipseConstants {
   
    public static final String LOG_PREFIX = "M2E-FB";
    public static final String MAVEN_PLUGIN_GROUPID = "org.codehaus.mojo";
    public static final String MAVEN_PLUGIN_ARTIFACTID = "findbugs-maven-plugin";
    public static final String FB_PREFS_FILE = ".fbprefs";
    public static final String FB_EXCLUDE_FILTER_FILE =
        ".fbExcludeFilterFile";
    public static final String FB_INCLUDE_FILTER_FILE =
        ".fbIncludeFilterFile";
    
    private FindbugsEclipseConstants() {
        //no instantiation.
    }
    
}
