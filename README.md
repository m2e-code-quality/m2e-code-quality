# m2e-code-quality [![Build Status](https://travis-ci.org/m2e-code-quality/m2e-code-quality.svg?branch=develop)](https://travis-ci.org/m2e-code-quality/m2e-code-quality)

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

## Installation

The latest release can be installed from the Eclipse Update Site at 
[https://m2e-code-quality.github.io/m2e-code-quality-p2-site/](https://m2e-code-quality.github.io/m2e-code-quality-p2-site/)

## Development environment

This project uses [Oomph](https://projects.eclipse.org/projects/tools.oomph), and you can set up an Eclipse development environment with the following steps:

1. Make sure your JRE/JDK has the JCE installed (See http://stackoverflow.com/questions/42128981/eclipse-pmd-plug-in-handshake-failure and https://github.com/pmd/pmd-eclipse-plugin/issues/19)
2. Downloading the Eclipse Installer from https://www.eclipse.org/downloads/ and start it.
3. On the initial page, click on the *Switch to advanced mode* button in the top right.
4. On the *Product* page, select *Eclipse for RCP and RAP Developers*.
5. On the *Projects* page, collapse the *Eclipse Projects* to scroll down to the *GitHub Projects* and select *m2e-code-quality*.
6. Make sure to choose the HTTPS anonymous option for the repository.
7. Choose other preferred installation settings on the *Variables* page.
8. Finish the wizard and watch your development environment being assembled.
9. Create an Eclipse run configuration. The target platform will automatically be setup correctly.

## Release

See [doc/release.md](doc/release.md) for information about how all of this is released.

## Mailing list

Activity here is 'coordinated' on a mailing list:
`m2e-code-quality@googlegroups.com`.

