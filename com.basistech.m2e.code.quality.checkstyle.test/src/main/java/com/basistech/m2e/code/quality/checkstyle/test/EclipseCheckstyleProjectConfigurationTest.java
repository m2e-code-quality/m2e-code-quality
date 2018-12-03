/*******************************************************************************
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.checkstyle.test;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import com.basistech.m2e.code.quality.shared.test.AbstractMavenProjectConfiguratorTestCase;

import net.sf.eclipsecs.core.builder.CheckstyleBuilder;
import net.sf.eclipsecs.core.builder.CheckstyleMarker;
import net.sf.eclipsecs.core.jobs.RunCheckstyleOnFilesJob;
import net.sf.eclipsecs.core.nature.CheckstyleNature;
import net.sf.eclipsecs.core.projectconfig.IProjectConfiguration;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationFactory;

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
		new TriggerCheckstyleExplicitly().call(p);
		assertMarkers(p, MARKER_ID, 1);
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

	public void testCheckstyleMultipleExecutions() throws Exception {
		final IProject p = importProject("projects/checkstyle-multi-check/pom.xml");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		IProjectConfiguration configuration = ProjectConfigurationFactory.getConfiguration(p);
		assertEquals(2, configuration.getLocalCheckConfigurations().size());
	}

	public void testCheckstyleMultipleExecutionsSkipOne() throws Exception {
		final IProject p = importProjectWithProfiles("projects/checkstyle-multi-check/pom.xml", "skip-second");
		assertTrue(p.exists());

		// must have nature and builder
		assertTrue(p.hasNature(NATURE_ID));
		assertTrue(hasBuilder(p, BUILDER_ID));

		// still both configurations present
		IProjectConfiguration configuration = ProjectConfigurationFactory.getConfiguration(p);
		assertEquals(2, configuration.getLocalCheckConfigurations().size());
	}

	public void testCheckstyleMultipleExecutionsSkipAll() throws Exception {
		final IProject p = importProjectWithProfiles("projects/checkstyle-multi-check/pom.xml", "skip");
		assertTrue(p.exists());

		// must have neither nature nor builder!
		assertFalse(p.hasNature(NATURE_ID));
		assertFalse(hasBuilder(p, BUILDER_ID));

		// still both configurations present
		IProjectConfiguration configuration = ProjectConfigurationFactory.getConfiguration(p);
		assertEquals(2, configuration.getLocalCheckConfigurations().size());
	}

	private final class TriggerCheckstyleExplicitly implements ProjectCallable {

		@Override
		public void call(IProject project) throws Exception {
			List<IFile> filesToCheck = new ArrayList<IFile>();
			collectFiles(project, filesToCheck);

			RunCheckstyleOnFilesJob job = new RunCheckstyleOnFilesJob(filesToCheck);
			job.setRule(job);
			job.schedule();
			job.join();
		}

		private void collectFiles(final IResource resource, final List<IFile> files) throws CoreException {

			if (!resource.isAccessible()) {
				return;
			}

			if (resource instanceof IFile) {
				files.add((IFile) resource);
			} else if (resource instanceof IContainer) {
				for (IResource member : ((IContainer) resource).members()) {
					collectFiles(member, files);
				}
			}
		}
	}
}
