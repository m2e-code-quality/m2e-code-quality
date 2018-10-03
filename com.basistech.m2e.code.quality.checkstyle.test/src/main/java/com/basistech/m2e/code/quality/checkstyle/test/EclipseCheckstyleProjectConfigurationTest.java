/*******************************************************************************
 * Copyright (c) 2012-2013 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.checkstyle.test;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;

@SuppressWarnings("restriction")
public class EclipseCheckstyleProjectConfigurationTest extends AbstractMavenProjectTestCase {

	private static final String CHECKSTYLE_MARKER = "net.sf.eclipsecs.core.CheckstyleMarker";

	public void testFindbugsCheck() throws Exception {
		runCheckstyleAndFindMarkers("projects/checkstyle-check/pom.xml", 13);
	}

	private void runCheckstyleAndFindMarkers(final String path, final int minimumMarkerCount) throws Exception {
		final IProject p = importProject(path);

		p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		waitForJobsToComplete();

		final IMarker[] markers = p.findMarkers(CHECKSTYLE_MARKER, true, IResource.DEPTH_INFINITE);
		assertTrue(markers.length >= minimumMarkerCount);
	}

}
