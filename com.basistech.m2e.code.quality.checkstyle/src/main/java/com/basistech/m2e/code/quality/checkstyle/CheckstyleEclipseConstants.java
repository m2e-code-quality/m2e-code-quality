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
package com.basistech.m2e.code.quality.checkstyle;


/**
 * Constants as it relates to eclipse checkstyle plugin
 */
public final class CheckstyleEclipseConstants {
   
    public static final String LOG_PREFIX = "M2E-CS";
    public static final String MAVEN_PLUGIN_GROUPID = "org.apache.maven.plugins";
    public static final String MAVEN_PLUGIN_ARTIFACTID = "maven-checkstyle-plugin";
    public static final String ECLIPSE_CS_PREFS_FILE = ".checkstyle";
    public static final String ECLIPSE_CS_PREFS_CONFIG_NAME = "maven-checkstyle-plugin";
    public static final String ECLIPSE_CS_CACHE_FILENAME = "${project_loc}/target/checkstyle-cachefile";
    public static final String ECLIPSE_CS_GENERATE_FORMATTER_SETTINGS = "eclipseCheckstyleGenerateFormatterSettings";
    
    private CheckstyleEclipseConstants() {
        //no instantiation.
    }
    
}
