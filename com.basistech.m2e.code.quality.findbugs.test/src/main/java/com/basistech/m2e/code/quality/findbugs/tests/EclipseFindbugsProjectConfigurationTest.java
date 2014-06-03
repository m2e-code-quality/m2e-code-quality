/*******************************************************************************
 * Copyright (c) 2012-2013 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.findbugs.tests;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;

import de.tobject.findbugs.builder.FindBugsWorker;
import de.tobject.findbugs.builder.ResourceUtils;
import de.tobject.findbugs.builder.WorkItem;

@SuppressWarnings("restriction")
public class EclipseFindbugsProjectConfigurationTest extends
        AbstractMavenProjectTestCase {

	private static final String FINDBUGS_MARKER =
	        "edu.umd.cs.findbugs.plugin.eclipse.findbugsMarker";

	public void testFindBugs1() throws Exception {
		IProject p = importProject("projects/findbugs-1/pom.xml");

		p.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		StructuredSelection selection = new StructuredSelection(p);

		Map<IProject, List<WorkItem>> projectMap =
		        ResourceUtils.getResourcesPerProject(selection);

		for (Map.Entry<IProject, List<WorkItem>> e : projectMap.entrySet()) {
			FindBugsWorker worker = new FindBugsWorker(p, monitor);
			worker.work(e.getValue());
		}

		IMarker[] markers =
		        p.findMarkers(FINDBUGS_MARKER, true, IResource.DEPTH_INFINITE);
		assertEquals(2, markers.length);
	}
}
