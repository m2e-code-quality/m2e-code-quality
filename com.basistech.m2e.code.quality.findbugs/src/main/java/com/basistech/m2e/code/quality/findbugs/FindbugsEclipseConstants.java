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
package com.basistech.m2e.code.quality.findbugs;

public final class FindbugsEclipseConstants {

	public static final String LOG_PREFIX = "M2E-FB";
	public static final String ECLIPSE_FB_PREFS_FILE = ".fbprefs";
	public static final String ECLIPSE_FB_NATURE_ID =
			// NB: SpotBugs still uses the old FindBugs Eclipse Project Nature ID, see https://github.com/spotbugs/spotbugs/blob/master/eclipsePlugin/plugin.xml
	        "edu.umd.cs.findbugs.plugin.eclipse.findbugsNature";
	public static final String FB_EXCLUDE_FILTER_FILE = ".fbExcludeFilterFile";
	public static final String FB_INCLUDE_FILTER_FILE = ".fbIncludeFilterFile";
	public static final String VISITORS = "visitors";
	public static final String THRESHOLD = "threshold";
	public static final String OMIT_VISITORS = "omitVisitors";
	public static final String PRIORITY = "priority";
	public static final String EFFORT = "effort";
	public static final String DEBUG = "debug";
	public static final String BUG_CATEGORIES = "bugCategories";
	public static final String EXCLUDE_FILTER_FILE = "excludeFilterFile";
	public static final String INCLUDE_FILTER_FILE = "includeFilterFile";
	public static final String MAX_RANK = "maxRank";

	private FindbugsEclipseConstants() {
		// no instantiation.
	}

}
