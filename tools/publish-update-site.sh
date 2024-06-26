#!/bin/bash -eu

if [[ "${GITHUB_REF}" != refs/tags/* && "${GITHUB_REF}" != refs/heads/develop ]]; then
    echo "${GITHUB_REF} is neither develop branch nor a tag!"
    # exit with success to just skip this part of the build and not fail the build
    exit 0
fi

# stop on first error
set -eu

#
# This script requires two tokens as env vars:
# * SITE_DEPLOY_PRIVATE_KEY: This is the private ssh key used to push to
#   to git@github.com:m2e-code-quality/m2e-code-quality-p2-site.git
#   Since this script is called from a workflow from repo m2e-code-quality, the default
#   GITHUB_TOKEN won't work to commit to a different repo (m2e-code-quality-p2-site).
#   The corresponding public key is configured on the repo m2e-code-quality-p2-site
#   as a deploy key.
#
# * GITHUB_TOKEN: Is used to fetch information to generate a changelog.
#


GIT_ROOT_DIR="$(cd "$(dirname "$0")" && git rev-parse --show-toplevel)"

RELEASE_TAG=""
if [[ "${GITHUB_REF}" == refs/tags/* ]]; then
    RELEASE_TAG=${GITHUB_REF##refs/tags/}
fi
CURRENT_SITE_FOLDER=current-site
SITE_GITHUB_BRANCH=gh-pages
SITE_GITHUB_REPO=m2e-code-quality/m2e-code-quality-p2-site.git
SITE_GITHUB_REPO_URL="git@github.com-repo-p2-site:${SITE_GITHUB_REPO}"
SITE_DEPLOY_PRIVATE_KEY_PATH="${HOME}/.ssh/m2e-code-quality-p2-site_deploy_key"

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
  <properties size='2'>
    <property name='p2.timestamp' value='${now}'/>
    <property name='p2.compressed' value='true'/>
  </properties>
  <children size='${countChildren}'>" > "${targetFolder}/compositeContent.xml"
    for sd in $subdirs; do
        echo "    <child location='${sd}'/>" >> "${targetFolder}/compositeContent.xml"
    done
    echo "</children>
</repository>
" >> "${targetFolder}/compositeContent.xml"

    echo "<?xml version='1.0' encoding='UTF-8'?><?compositeArtifactRepository version='1.0.0'?>
<repository name='${SITE_NAME}' type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>
  <properties size='2'>
    <property name='p2.timestamp' value='${now}'/>
    <property name='p2.compressed' value='true'/>
  </properties>
  <children size='${countChildren}'>" > "${targetFolder}/compositeArtifacts.xml"
    for sd in $subdirs; do
        echo "    <child location='${sd}'/>" >> "${targetFolder}/compositeArtifacts.xml"
    done
    echo "  </children>
</repository>
" >> "${targetFolder}/compositeArtifacts.xml"
}

## -- setup ssh
mkdir -p ~/.ssh
echo "${SITE_DEPLOY_PRIVATE_KEY}" > "${SITE_DEPLOY_PRIVATE_KEY_PATH}"
chmod 400 "${SITE_DEPLOY_PRIVATE_KEY_PATH}"
echo "
Host github.com-repo-p2-site
        Hostname github.com
        IdentityFile=${SITE_DEPLOY_PRIVATE_KEY_PATH}
" > "${HOME}/.ssh/config"


## -- fetch current site
rm -rf "${CURRENT_SITE_FOLDER}"
mkdir "${CURRENT_SITE_FOLDER}"
pushd "${CURRENT_SITE_FOLDER}"
git init -q --initial-branch="${SITE_GITHUB_BRANCH}"
# configure git
# see https://github.com/orgs/community/discussions/26560 and https://api.github.com/users/github-actions[bot]
git config --local user.name "github-actions[bot]"
git config --local user.email "41898282+github-actions[bot]@users.noreply.github.com"
git remote add origin "${SITE_GITHUB_REPO_URL}"
git pull --rebase origin "${SITE_GITHUB_BRANCH}"
popd

## -- integrate (copy) new version to the site
if [ -n "$RELEASE_TAG" ]; then
  rm -rf "${CURRENT_SITE_FOLDER:?}/${RELEASE_TAG}" \
    && mkdir "${CURRENT_SITE_FOLDER}/${RELEASE_TAG}" \
    && cp -R "${NEW_SITE_FOLDER}"/* "${CURRENT_SITE_FOLDER}/${RELEASE_TAG}/"
else
  rm -rf "${CURRENT_SITE_FOLDER:?}/snapshot" \
    && mkdir "${CURRENT_SITE_FOLDER}/snapshot" \
    && cp -R "${NEW_SITE_FOLDER}"/* "${CURRENT_SITE_FOLDER}/snapshot/"
fi

## -- regenerate composite meta data
STABLE_RELEASES="$(find ${CURRENT_SITE_FOLDER}/* -maxdepth 0 -type d -name "[0-9]\.[0-9].[0-9]" -printf '%f\n')"

regenCompositeMetadata "${STABLE_RELEASES}" "${CURRENT_SITE_FOLDER}/"

## -- update index.md
END_LINE="$(grep -n "@@RELEASE_LIST_MARKER@@" "${CURRENT_SITE_FOLDER}/index.md" | head -1 | cut -f 1 -d ':')"
INDEX_MD_CONTENT="$(head -$END_LINE "${CURRENT_SITE_FOLDER}/index.md")"
echo "$INDEX_MD_CONTENT" > "${CURRENT_SITE_FOLDER}/index.md"
for release in $STABLE_RELEASES; do
  echo "* [${release}](${release}/)" >> "${CURRENT_SITE_FOLDER}/index.md"
done


## -- generate changelog
if [ -n "$RELEASE_TAG" ]; then
    # extract the release notes
    END_LINE=$(grep -n "^## " CHANGELOG.md|head -2|tail -1|cut -d ":" -f 1)
    END_LINE=$((END_LINE - 1))
    head -$END_LINE CHANGELOG.md > "${CURRENT_SITE_FOLDER}/${RELEASE_TAG}/CHANGELOG.md"
else
    # Create release notes (snapshot)
    bundle exec github_changelog_generator \
        -t "${GITHUB_TOKEN}" \
        --output "${CURRENT_SITE_FOLDER}/snapshot/CHANGELOG.md" \
        --unreleased-only \
        --future-release "develop" \
        --no-verbose
fi

## -- create a new single commit
pushd "${CURRENT_SITE_FOLDER}"
git checkout --orphan=gh-pages-2
git add -A
git commit -a -m "Update ${SITE_GITHUB_REPO}"
git push --force origin "gh-pages-2:${SITE_GITHUB_BRANCH}"
popd

## -- cleanup: remove ssh key
rm "${SITE_DEPLOY_PRIVATE_KEY_PATH}"
