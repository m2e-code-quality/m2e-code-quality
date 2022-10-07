# m2e-code-quality build on github actions

The project uses [github actions](https://docs.github.com/en/actions) to CI builds.
Every push/pull request is built automatically including tests.
For every push, the snapshot update site is updated.

## Secrets

These secrets are configured for
[m2e-code-quality](https://github.com/m2e-code-quality/m2e-code-quality/settings/secrets/actions):

### GITHUB_TOKEN

That's the default token that is issued by github actions. It allows to push to the
same repo as the action has been started. This token is used to generate a changelog
and create a release.

During the release, also some commits are done. These use the identity of
[github-actions[bot]](https://api.github.com/users/github-actions[bot]).

### KEYSTORE_PASSWORD

The password for the keystore to sign the plugin is configured as a repository secret
named `KEYSTORE_PASSWORD`.

### SITE_DEPLOY_PRIVATE_KEY

That's the private ssh key used to push to the repository `m2e-code-quality-p2-site`.
The public key is add in the settings of
[m2e-code-quality-p2-site](https://github.com/m2e-code-quality/m2e-code-quality-p2-site/settings/keys)
with write access.

This secret is written to `~/.ssh/m2e-code-quality-p2-site_deploy_key` in the script
`tools/publish-update-site.sh`.

For the commit, the identity of [github-actions[bot]](https://api.github.com/users/github-actions[bot]) is used.

## What is being done

See the [build workflow](.github/workflows/build.yml).

The project is built with `./mvnw clean verify`.
A ready to use update site is available as `com.basistech.m2e.code.quality.site/target/repository`.

If github actions is running for a push on the main development branch `develop`,
then the script [publish-update-site.sh](tools/publish-update-site.sh)
is executed.

This will temporarily clone the site repo `m2e-code-quality-p2-site` and integrate the new
generated snapshot update site into it.

An updated changelog is generated via
[github-changelog-generator](https://github.com/github-changelog-generator/github-changelog-generator)
and also placed into the site repo.

As last step, the site repo is pushed to github using the deploy key and github will publish it via github pages.

## Signing

The plugin is signed with a certificate for CN=m2e-code-quality.github.io. This certificate has
been issued by Let's Encrypt.

The keystore is `tools/code-signing.p12`.

The password is in the env var `KEYSTORE_PASSWORD` which is configured as a secret.

Whether signing should be done or not is decided in the small build script `tools/build.sh`.

