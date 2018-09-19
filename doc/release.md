m2e-code-quality build and release
==================================

m2e-code-quality is built with Tycho. Tycho has warts. One of them is a lack of integration
with the maven-release-plugin. So, as of now, all 'releases' of m2e-code-quality have been
tagged snapshots.

To make a release:

1. Run a build. Use Apache Maven version 3.0.3 or newer. Always run mvn clean install;
Tycho has some stupid bug that goes off if you don't start from clean.

2. Test as you feel the urge using the plugins and features.

3. The build produces a p2 update site in com.basistech.m2e.code.quality.site/target/site. Ignore the
ZIP file; it just contains the site.xml. To publish a release, this content has to go onto the web
somewhere. What we do is put it into the gh-pages branch of this repository. This makes the
repository get bigger and bigger, since nothing is ever deleted. There are a series of directories in
here, one per release. Hypothetically, we could pile up versions in one P2 site, but the Eclipse doc
on the tools is so excrable that I never figured it out.

4. Once you push some version to the site, it's a good idea to make a git tag for it.

5. The index.html at the top of the gh-pages for the m2e-code-quality has some general explanation,
maintain it.

6. There's also a web site for the organization; it's the me2-code-quality.github.com repo.
