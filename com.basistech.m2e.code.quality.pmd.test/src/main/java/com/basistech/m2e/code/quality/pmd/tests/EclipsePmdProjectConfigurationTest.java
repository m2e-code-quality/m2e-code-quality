/*******************************************************************************
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.pmd.tests;

import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;

import com.basistech.m2e.code.quality.shared.test.AbstractMavenProjectConfiguratorTestCase;

import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.builder.MarkerUtil;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDBuilder;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDNature;
import net.sourceforge.pmd.eclipse.runtime.cmd.MarkerInfo2;
import net.sourceforge.pmd.eclipse.runtime.cmd.ReviewCodeCmd;

public class EclipsePmdProjectConfigurationTest extends AbstractMavenProjectConfiguratorTestCase {

	private static final String MARKER_ID = PMDRuntimeConstants.PMD_MARKER;
	private static final String NATURE_ID = PMDNature.PMD_NATURE;
	private static final String BUILDER_ID = PMDBuilder.PMD_BUILDER;

	public void testPmdCheck() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/pmd-check/pom.xml", MARKER_ID, 3);
	}

	public void testPmdPresent() throws Exception {
		final IProject p = importProject("projects/pmd-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));
	}

	public void testPmdSkip() throws Exception {
		final IProject p = importProjectWithProfiles("projects/pmd-check/pom.xml", "skip");
		assertTrue(p.exists());

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));
	}

	public void testPmdReconfigureSkip() throws Exception {
		final IProject p = importProject("projects/pmd-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		// run the build -> markers
		runBuild(p);
		assertMarkers(p, MARKER_ID, 1);

		refreshProjectWithProfiles(p, "skip");

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));

		// no remaining markers
		assertNoMarkers(p, MARKER_ID);

		// building alone does not produces markers
		runBuild(p);
		assertNoMarkers(p, MARKER_ID);

		// explicitly running produces the markers
		assertMarkers(p, MARKER_ID, 1);
	}

	public void testPmdReconfigureReactivate() throws Exception {
		final IProject p = importProject("projects/pmd-check/pom.xml");
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

	protected Map<IFile, Set<MarkerInfo2>> triggerPmd(final IProject project) throws Exception {
		ReviewCodeCmd cmd = new ReviewCodeCmd();
		cmd.addResource(project);
		cmd.setStepCount(1);
		cmd.setTaskMarker(true);
		cmd.setOpenPmdPerspective(false);
		cmd.setOpenPmdViolationsOverviewView(false);
		cmd.setOpenPmdViolationsOutlineView(false);
		cmd.setUserInitiated(true);
		cmd.setRunAlways(false);
		cmd.performExecute();

		cmd.join();
		return cmd.getMarkers();
	}

	@Override
	protected void assertNoMarkers(IProject project, String markerId) throws Exception {
		final IMarker[] markers = MarkerUtil.findAllMarkers(project);
		assertEquals(0, markers.length);
	}

	@Override
	protected void assertMarkers(final IProject project, final String markerId, final int minimumMarkerCount)
			throws Exception {

		Map<IFile, Set<MarkerInfo2>> markers = triggerPmd(project);

		assertEquals(markers.size(), 1);
		assertTrue(markers.values().iterator().next().size() >= minimumMarkerCount);
	}
}
