# m2e-code-quality build on github actions

The project uses [github actions](https://docs.github.com/en/actions) to CI builds.
Every push/pull request is built automatically including tests.
For every push, the snapshot update site is updated.

## Secrets

For publishing the update site and generating a changelog, a github token is required.

The token is configured as a repository secret named `PUBLISH_SITE_TOKEN`.

The github action is called from the main code repository `m2e-code-quality`. The default
GITHUB_TOKEN provided by github actions is not working, because the update site
is in a separate repository `m2e-code-quality-p2-site`.

The token needs the scope "public_repo". A new token can be created at <https://github.com/settings/tokens>.
The user, to which the token belongs, needs to have write permissions for `m2e-code-quality-p2-site`
in order to commit the updated site.

For the commit, the identity of [m2e-code-quality-bot](https://github.com/m2e-code-quality-bot) is used.

## What is being done

See the [build workflow](.github/workflows/build.yml).

The project is built with `./mvnw clean verify`.
A ready to use update site is available as `com.basistech.m2e.code.quality.site/target/repository`.

If github actions is running for a push on the main development branch `develop`,
then the script [publish-update-site.sh](tools/publish-update-site.sh)
is executed.

This will temporarily clone the site repo `m2e-code-quality-p2-site` and integrate the new
generated snapshot update site into it.

An updated changelog is generated via [github-changelog-generator](https://github.com/github-changelog-generator/github-changelog-generator)
and also placed into the site repo.

As last the, the site repo is pushed to github and github will publish it via github pages.

