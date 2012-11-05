m2e-code-quality build and release
==================================

We are cooperating with JvZ of Sonatype to manage releases of these
plugins. This saves us from creating an ever-more-enormous github
history of update sites, and helps compensate for the lack of release
support in Tycho.

Note that the procedure here is new and in process. Contact
bimargulies if you feel the need to push out a release before this is
complete and stable.

To make a release:

1. Run a build. Use Apache Maven version 3.0.3 or newer. Always run mvn clean install;
Tycho has some stupid bug that goes off if you don't start from clean.
2. Test as you feel the urge using the plugins and features.
3. Make a tag in git.
4. Update the [offical catalog](https://github.com/tesla/m2e-discovery-catalog); there's a
fork of this repo in our organization, or you can use your own fork to
make a pull request. The catalog points to the output of a CI build at
Sonatype; hopefully, it will be easy to tell what tag got built, but
you may have to match up commits.

(There's also a web site for the organization; it's the me2-code-quality.github.com repo.)

However, it turns out that Sonatype is not yet pulling and building us, so we still need
to make releases by pushing them to the github pages of the repo.

This happens by cd-ing to the site project and running mvn -PpublishSite.

