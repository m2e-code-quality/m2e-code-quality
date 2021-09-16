#!/bin/bash -e

#
# Available env vars: https://docs.github.com/en/actions/reference/environment-variables#default-environment-variables
#

signing_options=()

if [[ "${RUNNER_OS}" == "Linux"
        && "${GITHUB_EVENT_NAME}" == "push"
        && "${GITHUB_REPOSITORY}" == "m2e-code-quality/m2e-code-quality"
        && "${GITHUB_REF}" == "refs/heads/develop"
        && -n "${KEYSTORE_PASSWORD}" ]]; then

    signing_options=(
                     -Djarsigner.alias=code-signing
                     -Djarsigner.keystore="$(pwd)/tools/code-signing.p12"
                     -Djarsigner.keypass="${KEYSTORE_PASSWORD}"
                     -Djarsigner.storepass="${KEYSTORE_PASSWORD}"
                     -Djarsigner.tsa=http://timestamp.digicert.com
                     )
fi

./mvnw clean verify -e -B -V -ntp "${signing_options[@]}"
