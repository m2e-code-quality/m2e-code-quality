#!/bin/bash -eu

#
# This script requires the env var "GITHUB_TOKEN". It uses this token to commit
# to https://github.com/m2e-code-quality/m2e-code-quality-p2-site and
# fetch information to generate a changelog.
#
# Since this script is called from a workflow from repo m2e-code-quality, the default
# GITHUB_TOKEN won't work to commit to a different repo (m2e-code-quality-p2-site).
#


GIT_ROOT_DIR="$(cd "$(dirname "$0")" && git rev-parse --show-toplevel)"
OLD_RELEASES_FILE=${GIT_ROOT_DIR}/tools/old_releases.list

TRAVIS_TAG=""
CURRENT_SITE_FOLDER=current-site
SITE_GITHUB_BRANCH=gh-pages
SITE_GITHUB_REPO=m2e-code-quality/m2e-code-quality-p2-site
SITE_GITHUB_REPO_URL="https://${GITHUB_TOKEN}@github.com/${SITE_GITHUB_REPO}"

NEW_SITE_FOLDER=com.basistech.m2e.code.quality.site/target/repository/

SITE_NAME="M2E Code Quality - Eclipse Update Site"

##
## adapted from
## https://github.com/jbosstools/jbosstools-build-ci/blob/jbosstools-4.4.x/util/cleanup/jbosstools-cleanup.sh#L255
##
function regenCompositeMetadata () {
  subdirs=$1
  targetFolder=$2

  now=$(date +%s000)

  countChildren=0
    for sd in $subdirs; do
    countChildren=$((countChildren + 1))
  done

    echo "<?xml version='1.0' encoding='UTF-8'?><?compositeArtifactRepository version='1.0.0'?>
<repository name='${SITE_NAME}' type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository' version='1.0.0'>
  <properties size='2'><property name='p2.timestamp' value='${now}'/><property name='p2.compressed' value='true'/></properties>
  <children size='${countChildren}'>" > "${targetFolder}/compositeContent.xml"
    for sd in $subdirs; do
        echo "    <child location='${sd}'/>" >> "${targetFolder}/compositeContent.xml"
    done
    echo "</children>
</repository>
" >> "${targetFolder}/compositeContent.xml"

    echo "<?xml version='1.0' encoding='UTF-8'?><?compositeArtifactRepository version='1.0.0'?>
<repository name='${SITE_NAME}' type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>
  <properties size='2'><property name='p2.timestamp' value='${now}'/><property name='p2.compressed' value='true'/></properties>
  <children size='${countChildren}'>" > "${targetFolder}/compositeArtifacts.xml"
    for sd in $subdirs; do
        echo "    <child location='${sd}'/>" >> "${targetFolder}/compositeArtifacts.xml"
    done
    echo "  </children>
</repository>
" >> "${targetFolder}/compositeArtifacts.xml"
}

## -- fetch current site
rm -rf "${CURRENT_SITE_FOLDER}"
mkdir "${CURRENT_SITE_FOLDER}"
pushd "${CURRENT_SITE_FOLDER}"
git init -q --initial-branch="${SITE_GITHUB_BRANCH}"
git config user.name "m2e-code-quality-bot"
git config user.email "m2e-code-quality-bot@users.noreply.github.com"
git remote add origin "${SITE_GITHUB_REPO_URL}"
git pull --rebase origin "${SITE_GITHUB_BRANCH}"
popd

## -- integrate (copy) new version to the site
if [ -n "$TRAVIS_TAG" ]; then
  rm -rf "${CURRENT_SITE_FOLDER:?}/${TRAVIS_TAG}" \
    && mkdir "${CURRENT_SITE_FOLDER}/${TRAVIS_TAG}" \
    && cp -R "${NEW_SITE_FOLDER}"/* "${CURRENT_SITE_FOLDER}/${TRAVIS_TAG}/"
else
  rm -rf "${CURRENT_SITE_FOLDER:?}/snapshot" \
    && mkdir "${CURRENT_SITE_FOLDER}/snapshot" \
    && cp -R "${NEW_SITE_FOLDER}"/* "${CURRENT_SITE_FOLDER}/snapshot/"
fi

## -- regenerate composite meta data
STABLE_RELEASES="$(cat "${OLD_RELEASES_FILE}") $(find ${CURRENT_SITE_FOLDER}/* -maxdepth 1 -type d -name "[0-9]\.[0-9].[0-9]" -printf '%f\n')"

regenCompositeMetadata "${STABLE_RELEASES}" "${CURRENT_SITE_FOLDER}/"

## -- generate changelog
# get current version
current_version=$(./mvnw --batch-mode --no-transfer-progress \
        org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate \
        -Dexpression=project.version -q -DforceStdout \
        -Dtycho.mode=maven)

# Create release notes (snapshot)
bundle exec github_changelog_generator \
    -t "${GITHUB_TOKEN}" \
    --output "${CURRENT_SITE_FOLDER}/snapshot/CHANGELOG.md" \
    --unreleased-only \
    --unreleased-label "${current_version} ($(date +%Y-%m-%d))"


# create a new single commit
pushd "${CURRENT_SITE_FOLDER}"
git checkout --orphan=gh-pages-2
git commit -a -m "Update ${SITE_GITHUB_REPO}"
git push --force origin "gh-pages-2:${SITE_GITHUB_BRANCH}"
popd

