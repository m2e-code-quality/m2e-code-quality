#!/bin/bash -e

#
# Available env vars: https://docs.github.com/en/actions/reference/environment-variables#default-environment-variables
#

signing_options=()

if [[ "${RUNNER_OS}" == "Linux"
        && "${GITHUB_EVENT_NAME}" == "push"
        && "${GITHUB_REPOSITORY}" == "m2e-code-quality/m2e-code-quality"
        && ( "${GITHUB_REF}" == "refs/heads/develop" || "${GITHUB_REF}" == refs/tags/* )
        && -n "${KEYSTORE_PASSWORD}" ]]; then

    signing_options=(
                     -Djarsigner.alias=code-signing
                     -Djarsigner.keystore="$(pwd)/tools/code-signing.p12"
                     -Djarsigner.keypass="${KEYSTORE_PASSWORD}"
                     -Djarsigner.storepass="${KEYSTORE_PASSWORD}"
                     -Djarsigner.tsa=http://timestamp.digicert.com
                     )
fi

# make org.jboss.tools.tycho-plugins:repository-utils happy:
# for pull requests it sees a ref "refs/remotes/pull/<PR-Number>/merge"
# but no remote named "pull" exists...
git remote add pull https://github.com/m2e-code-quality/m2e-code-quality

./mvnw clean verify -e -B -V -ntp "${signing_options[@]}"
