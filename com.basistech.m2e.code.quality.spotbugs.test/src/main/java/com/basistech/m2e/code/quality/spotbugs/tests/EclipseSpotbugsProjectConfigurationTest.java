/*******************************************************************************
 * Copyright (c) 2012-2013 Red Hat, Inc.
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.spotbugs.tests;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.StructuredSelection;

import com.basistech.m2e.code.quality.shared.test.AbstractMavenProjectConfiguratorTestCase;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.builder.FindBugsWorker;
import de.tobject.findbugs.builder.ResourceUtils;
import de.tobject.findbugs.builder.WorkItem;
import de.tobject.findbugs.marker.FindBugsMarker;

@SuppressWarnings("restriction")
public class EclipseSpotbugsProjectConfigurationTest extends AbstractMavenProjectConfiguratorTestCase {

	private static final String MARKER_ID = FindBugsMarker.NAME;
	private static final String NATURE_ID = FindbugsPlugin.NATURE_ID;
	private static final String BUILDER_ID = FindbugsPlugin.BUILDER_ID;

	public void testSpotbugsCheck() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/spotbugs-check/pom.xml", MARKER_ID, 2, new TriggerSpotbugsExplicitly());
	}

	public void testSpotbugsSpotbugs() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/spotbugs-spotbugs/pom.xml", MARKER_ID, 2, new TriggerSpotbugsExplicitly());
	}

	public void testSpotbugsPresent() throws Exception {
		final IProject p = importProject("projects/spotbugs-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));
	}

	public void testSpotbugsSkip() throws Exception {
		final IProject p = importProjectWithProfiles("projects/spotbugs-check/pom.xml", "skip");
		assertTrue(p.exists());

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));
	}

	public void testSpotbugsReconfigureSkip() throws Exception {
		final IProject p = importProject("projects/spotbugs-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		// run the build -> markers
		runBuild(p, new TriggerSpotbugsExplicitly());
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
		new TriggerSpotbugsExplicitly().call(p);
		assertMarkers(p, MARKER_ID, 1);
	}

	public void testSpotbugsReconfigureReactivate() throws Exception {
		final IProject p = importProject("projects/spotbugs-check/pom.xml");
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

	protected class TriggerSpotbugsExplicitly implements ProjectCallable {
		@Override
		public void call(IProject project) throws Exception {
			final StructuredSelection selection = new StructuredSelection(project);

			final Map<IProject, List<WorkItem>> projectMap = ResourceUtils.getResourcesPerProject(selection);

			for (final Map.Entry<IProject, List<WorkItem>> e : projectMap.entrySet()) {
				final FindBugsWorker worker = new FindBugsWorker(project, monitor);
				worker.work(e.getValue());
			}

			waitForJobsToComplete();
		}
	}
}
