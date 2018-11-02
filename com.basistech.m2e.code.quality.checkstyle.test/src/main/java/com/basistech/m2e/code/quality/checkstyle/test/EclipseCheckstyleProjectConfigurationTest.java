/*******************************************************************************
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.checkstyle.test;

import org.eclipse.core.resources.IProject;
import com.basistech.m2e.code.quality.shared.test.AbstractMavenProjectConfiguratorTestCase;

import net.sf.eclipsecs.core.builder.CheckstyleBuilder;
import net.sf.eclipsecs.core.builder.CheckstyleMarker;
import net.sf.eclipsecs.core.nature.CheckstyleNature;

public class EclipseCheckstyleProjectConfigurationTest extends AbstractMavenProjectConfiguratorTestCase {

	private static final String MARKER_ID = CheckstyleMarker.MARKER_ID;
	private static final String BUILDER_ID = CheckstyleBuilder.BUILDER_ID;
	private static final String NATURE_ID = CheckstyleNature.NATURE_ID;

	public void testCheckstyleCheck() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/checkstyle-check/pom.xml", MARKER_ID, 13);
	}

	public void testCheckstylePresent() throws Exception {
		final IProject p = importProject("projects/checkstyle-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));
	}

	public void testCheckstyleSkip() throws Exception {
		final IProject p = importProjectWithProfiles("projects/checkstyle-check/pom.xml", "skip");
		assertTrue(p.exists());

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));
	}

	public void testCheckstyleReconfigureSkip() throws Exception {
		final IProject p = importProject("projects/checkstyle-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		refreshProjectWithProfiles(p, "skip");

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));
	}

	public void testCheckstyleReconfigureReactivate() throws Exception {
		final IProject p = importProject("projects/checkstyle-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		refreshProjectWithProfiles(p, "skip");

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));

		refreshProjectWithProfiles(p, "");

		// must have nature and builder again
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));
	}
}
