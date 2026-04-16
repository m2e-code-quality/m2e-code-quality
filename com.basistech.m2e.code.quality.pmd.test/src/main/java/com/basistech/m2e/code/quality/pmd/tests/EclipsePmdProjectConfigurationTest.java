/*******************************************************************************
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.pmd.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.tests.common.JobHelpers;
import org.junit.Test;

import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDBuilder;
import net.sourceforge.pmd.eclipse.runtime.builder.PMDNature;
import net.sourceforge.pmd.eclipse.runtime.cmd.ReviewCodeCmd;

import com.basistech.m2e.code.quality.pmd.PmdEclipseConstants;
import com.basistech.m2e.code.quality.shared.test.AbstractMavenProjectConfiguratorTestCase;

@SuppressWarnings("restriction")
public class EclipsePmdProjectConfigurationTest extends AbstractMavenProjectConfiguratorTestCase {

	private static final String MARKER_ID = PMDRuntimeConstants.PMD_MARKER;
	private static final String NATURE_ID = PMDNature.PMD_NATURE;
	private static final String BUILDER_ID = PMDBuilder.PMD_BUILDER;

	@Test
	public void testPmdCheck() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/pmd-check/pom.xml", MARKER_ID, 3, new WaitForReviewCommand());
	}

	@Test
	public void testPmdPresent() throws Exception {
		final IProject p = importProject("projects/pmd-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));
	}

	@Test
	public void testPmdSkip() throws Exception {
		final IProject p = importProjectWithProfiles("projects/pmd-check/pom.xml", "skip");
		assertTrue(p.exists());

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));
	}

	@Test
	public void testPmdReconfigureSkip() throws Exception {
		final IProject p = importProject("projects/pmd-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		// run the build -> markers
		runBuild(p, new WaitForReviewCommand());
		assertMarkers(p, MARKER_ID, 1);

		refreshProjectWithProfiles(p, "skip");

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));

		// no remaining markers
		assertNoMarkers(p, MARKER_ID);

		// building alone does not produces markers
		runBuild(p, new WaitForReviewCommand());
		assertNoMarkers(p, MARKER_ID);

		// explicitly running produces the markers
		new TriggerPmdExplicitly().call(p);
		assertMarkers(p, MARKER_ID, 1);
	}

	@Test
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

	@Test
	public void testPmdCustomRuleset() throws Exception {
		importProjectRunBuildAndFindMarkers("projects/pmd-custom-ruleset/pom.xml", MARKER_ID, 1, new WaitForReviewCommand());
	}

	@Test
	public void testPmdCustomRulesetWithProperty() throws Exception {
		final String projectName = "pmd-custom-ruleset-with-property";
		importProjectRunBuildAndFindMarkers("projects/" + projectName + "/pom.xml", MARKER_ID, 1, new WaitForReviewCommand());

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		IFile generatedRuleset = project.getFile(PmdEclipseConstants.PMD_RULESET_FILE);
		StringWriter rulesetContent = new StringWriter();
		try (Reader in = new InputStreamReader(generatedRuleset.getContents(), StandardCharsets.UTF_8)) {
			in.transferTo(rulesetContent);
		}
		assertTrue(rulesetContent.toString().contains("value=\"42\""));
	}

	protected class TriggerPmdExplicitly implements ProjectCallable {
		@Override
		public void call(IProject project) throws Exception {
			ReviewCodeCmd cmd = new ReviewCodeCmd();
			cmd.addResource(project);
			cmd.setStepCount(1);
			cmd.setOpenPmdPerspective(false);
			cmd.setOpenPmdViolationsOverviewView(false);
			cmd.setOpenPmdViolationsOutlineView(false);
			cmd.setUserInitiated(true);
			cmd.setRunAlways(false);
			cmd.performExecute();

			cmd.join();
		}
	}

	protected class WaitForReviewCommand implements ProjectCallable {
		@Override
		public void call(IProject project) throws Exception {
			String name = new ReviewCodeCmd().getName();
			JobHelpers.waitForJobs(new JobHelpers.IJobMatcher() {
				@Override
				public boolean matches(Job job) {
					return job.getName().equals(name);
				}
			}, 60_000);
			waitForJobsToComplete();
		}
	}
}
