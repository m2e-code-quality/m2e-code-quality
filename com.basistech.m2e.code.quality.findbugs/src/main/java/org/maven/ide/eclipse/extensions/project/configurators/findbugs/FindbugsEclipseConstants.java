/*******************************************************************************
 * Copyright 2010 Mohan KR
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
