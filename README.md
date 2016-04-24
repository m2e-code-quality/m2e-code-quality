# m2e-code-quality #

This project provides Eclipse plugins that bridge the Maven and
Eclipse plugins for Checkstyle and PMD. In a Maven project that uses
Checkstyle or PMD, the project configures the tools via configuration
information the POM or pointed to by the POM.

On the other hand, the Eclipse plugins for these tools provide
annotations and as-you-edit notifications of issues. Thus, it's
desirable to turn the Maven plugins *off* in M2E, to turn the Eclipse
plugins *on*, and to move configuration from the Maven plugins to the
Eclipse plugins.

In a perfect world, there would be some sort of standardized API that
would allow a build tool like Maven to publish this configuration and
for IDE plugins to consume it. In reality, this has to be arranged,
one plugin-pair at a time, and depends rather delicately on the OSGi
manifests of the receiving plugins. If they don't export enough API
for programmatic configuration, it cannot be done.

These Eclipse plugins are built with the Tycho Maven plugin. The
marriage of OSGi, Eclipse, and Maven is imperfect. In particular, it
rarely works to run a build that does not start with 'clean'.

This project has Oomph, and you can set up an Eclipse development workspace of it in one click by:

1. Downloading the Eclipse Installer from https://www.eclipse.org/downloads/ and starting it.
2. On the initial page, click on the *Switch to advanced mode* button in the top right.
3. On the *Product* page, select *Eclipse for RCP and RAP Developers*.
4. On the *Projects* page, collapse the *Eclipse Projects* to scroll down to the *Github Projects* and select *m2e-code-quality*.
5. Choose your preferred installation settings on the *Variables* page.
6. Finish the wizard and watch how your development environment is being assembled, courtesy of Oomph.

See [doc/release.md](doc/release.md) for information about how all of this is released.

Activity here is 'coordinated' on a mailing list:
`m2e-code-quality@googlegroups.com`.

