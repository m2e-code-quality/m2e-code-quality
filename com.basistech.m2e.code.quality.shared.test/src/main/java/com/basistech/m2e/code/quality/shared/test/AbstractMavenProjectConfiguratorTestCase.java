/*******************************************************************************
 * Copyright (c) 2018 GEBIT Solutions GmbH
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.basistech.m2e.code.quality.shared.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstallType;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.internal.IMavenConstants;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;

@SuppressWarnings("restriction")
public abstract class AbstractMavenProjectConfiguratorTestCase extends AbstractMavenProjectTestCase {

	@Override
	public void setUp() throws Exception {
		addDefaultJavaVMIfNeeded();
		super.setUp();
	}

	/**
	 * Setup a default JVM if none exists yet. This is especially required for mac
	 * osx on github actions, as there is no default JVM detected automatically.
	 * This is not done via the following extension, in order to check for the
	 * existence of JAVA_HOME:
	 *
	 * <pre>
	 *    &lt;extension
	 *         point="org.eclipse.jdt.launching.vmInstalls">
	 *      &lt;vmInstall
	 *            home="${env_var:JAVA_HOME}"
	 *            id="com.basistech.m2e.code.quality.shared.defaultJvm"
	 *            name="defaultJvm"
	 *            vmInstallType="org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType">
	 *      &lt;/vmInstall>
	 *   &lt;/extension>
	 * </pre>
	 */
	private void addDefaultJavaVMIfNeeded() {
		if (JavaRuntime.getDefaultVMInstall() != null
				&& JavaRuntime.getDefaultVMInstall().getInstallLocation().exists()) {
			return;
		}

		String javaHomeEnv = System.getenv("JAVA8_HOME");
		if (javaHomeEnv == null) {
			javaHomeEnv = System.getenv("JAVA_HOME");
		}
		if (javaHomeEnv == null) {
			System.out.println("WARN: Neither JAVA8_HOME nor JAVA_HOME found!");
			return;
		}
		File javaHome = new File(javaHomeEnv);
		if (javaHome.exists()) {
			try {
				javaHome = javaHome.getCanonicalFile();
				IVMInstallType vmInstallType = JavaRuntime
						.getVMInstallType("org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType");
				IVMInstall vm = vmInstallType.createVMInstall("com.basistech.m2e.code.quality.shared.defaultJvm");
				vm.setInstallLocation(javaHome);
				vm.setName("defaultJvm");
				NullProgressMonitor monitor = new NullProgressMonitor();
				JavaRuntime.setDefaultVMInstall(vm, monitor);
			} catch (IOException | CoreException e) {
				System.out.println("ERROR: Error setting up default JVM " + javaHome + ": " + e);
			}
		} else {
			System.out.println("ERROR: Path " + javaHome + " does not exist");
		}
	}

	protected boolean hasBuilder(final IProject project, final String id) throws Exception {
		for (ICommand cmd : project.getDescription().getBuildSpec()) {
			if (id.equals(cmd.getBuilderName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected IProject importProject(String pomLocation) throws IOException, CoreException {
		try {
			IProject result = super.importProject(pomLocation);
			waitForJobsToComplete();
			return result;
		} catch (InterruptedException ex) {
			throw new CoreException(
					new Status(IStatus.ERROR, IMavenConstants.PLUGIN_ID, "Failed to wait for completion", ex));
		}
	}

	protected IProject importProjectWithProfiles(final String pomFile, final String profiles) throws Exception {
		ResolverConfiguration resolver = new ResolverConfiguration();
		resolver.setSelectedProfiles(profiles);
		final IProject p = importProject(pomFile, resolver);
		waitForJobsToComplete();
		return p;
	}

	protected void refreshProjectWithProfiles(final IProject project, final String profiles) throws Exception {
		IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(project);
		ResolverConfiguration resolverConfig = facade.getResolverConfiguration();
		resolverConfig.setSelectedProfiles(profiles);

		IProjectConfigurationManager projectManager = MavenPlugin.getProjectConfigurationManager();
		projectManager.setResolverConfiguration(project, resolverConfig);
		refreshMavenProject(project);
		waitForJobsToComplete();
	}

	protected void importProjectRunBuildAndFindMarkers(final String path, final String markerId,
			final int minimumMarkerCount) throws Exception {
		importProjectRunBuildAndFindMarkers(path, markerId, minimumMarkerCount, null);
	}

	protected void importProjectRunBuildAndFindMarkers(final String path, final String markerId,
			final int minimumMarkerCount, final ProjectCallable extras) throws Exception {
		final IProject p = importProject(path);

		runBuildAndAssertMarkers(p, markerId, minimumMarkerCount, extras);
	}

	protected void runBuildAndAssertMarkers(final IProject project, final String markerId, final int minimumMarkerCount)
			throws Exception {
		runBuildAndAssertMarkers(project, markerId, minimumMarkerCount, null);
	}

	protected void runBuildAndAssertMarkers(final IProject project, final String markerId, final int minimumMarkerCount,
			final ProjectCallable extras) throws Exception {
		runBuild(project, extras);

		assertMarkers(project, markerId, minimumMarkerCount);
	}

	protected void runBuild(final IProject project) throws Exception {
		runBuild(project, null);
	}

	protected void runBuild(final IProject project, final ProjectCallable extras) throws Exception {
		project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
		waitForJobsToComplete();

		if (extras != null) {
			extras.call(project);
		}
	}

	protected IMarker[] findMarkers(final IProject project, final String markerId) throws Exception {
		return project.findMarkers(markerId, true, IResource.DEPTH_INFINITE);
	}

	protected void assertMarkers(final IProject project, final String markerId, final int minimumMarkerCount)
			throws Exception {
		final IMarker[] markers = findMarkers(project, markerId);
		assertTrue(markers.length >= minimumMarkerCount);
	}

	protected void assertNoMarkers(final IProject project, final String markerId) throws Exception {
		final IMarker[] markers = findMarkers(project, markerId);
		assertEquals(0, markers.length);
	}

	/**
	 * Like {@link java.util.concurrent.Callable} but with an {@link IProject}
	 * argument and without return type.
	 */
	public interface ProjectCallable {
		public void call(IProject project) throws Exception;
	}
}
