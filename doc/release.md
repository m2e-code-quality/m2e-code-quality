m2e-code-quality build and release
==================================

m2e-code-quality is built with Tycho.

To make a release:

1. Run a build. Use Apache Maven version 3.0.3 or newer. Always run `mvn clean install`;
Tycho has some stupid bug that goes off if you don't start from clean.

2. Test as you feel the urge using the plugins and features.

3. The index.html at the top of the gh-pages for the m2e-code-quality has some general explanation,
maintain it.

4. Release notes are generated automatically from merged PR and closed issues. If you want some custom
[release summary](https://github.com/github-changelog-generator/github-changelog-generator#using-the-summary-section-feature),
create a new GitHub Issue, set the label `release-summary` and add it to a GitHub milestone named after the intended
release version (see next section). The description of the issue will then be used as the release summary.

5. When you're satisfied and all changes are pushed to the develop branch, tag the head
commit with a 3-digit version number (e.g. 1.1.0). This will automatically build a site,
publish it to
[https://m2e-code-quality.github.io/m2e-code-quality-p2-site/](https://m2e-code-quality.github.io/m2e-code-quality-p2-site/),
and update the development branch to the next version number. Please note that the produced artifacts
will always have a 4 digit version number, otherwise the release build would be newer then the last snapshot build
in OSGi's version ordering. A release build is marked with a -r version qualifier. This will also produce a
[release](https://github.com/m2e-code-quality/m2e-code-quality/releases) in GitHub.

6. Just in case something failed you can repeat step 5 by moving the tag. A re-spin will completely replace the version
in the P2 repository.

That's it!