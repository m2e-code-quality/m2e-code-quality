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
package com.basistech.m2e.code.quality.findbugs;

import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.ECLIPSE_FB_NATURE_ID;
import static com.basistech.m2e.code.quality.findbugs.FindbugsEclipseConstants.ECLIPSE_FB_PREFS_FILE;

import org.eclipse.core.resources.IProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.basistech.m2e.code.quality.shared.EclipseConfigManager;
import com.google.common.base.Preconditions;

import de.tobject.findbugs.nature.FindBugsNature;

/**
 * An extension of the {@code EclipseConfigManager} which provides nature, id 
 * and config file location for FindBugs.
 * 
 */
public class EclipseFindbugsConfigManager extends EclipseConfigManager {

	private static final Logger LOG =
	        LoggerFactory.getLogger(EclipseFindbugsConfigManager.class);

	private EclipseFindbugsConfigManager(final FindBugsNature fbNature) {
		super(fbNature, ECLIPSE_FB_NATURE_ID, ECLIPSE_FB_PREFS_FILE);
	}

	public static EclipseFindbugsConfigManager newInstance(
	        final IProject project) {
		LOG.debug("entering newInstance");
		Preconditions.checkNotNull(project);
		final FindBugsNature fbNature = new FindBugsNature();
		fbNature.setProject(project);
		return new EclipseFindbugsConfigManager(fbNature);
	}
}
