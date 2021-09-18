#!/bin/bash -eu

if [[ "${GITHUB_REF}" != refs/tags/* ]]; then
    echo "${GITHUB_REF} is not a tag!"
    exit 1
fi



# stop on first error
set -eu

RELEASE_TAG=${GITHUB_REF##refs/tags/}
RELEASE_BRANCH=master

echo "Preparing release ${RELEASE_TAG} on branch ${RELEASE_BRANCH}"

# configure git
git config --local user.name "m2e-code-quality-bot"
git config --local user.email "m2e-code-quality-bot@users.noreply.github.com"

# verify same commit as branch
HEAD=$(git rev-parse --verify HEAD)
GITHUB_BRANCH=$(git ls-remote -q --refs | grep "$HEAD" | awk '{print $2}' | grep '^refs/heads' | sed -e 's/^refs\/heads\///')

C=$(echo "$GITHUB_BRANCH" | wc -w)
if [ "$C" -ne "1" ]; then echo "Tag cannot be resolved to branch name" && exit 1; fi
if [ "$GITHUB_BRANCH" != "develop" ]; then echo "Tag is not on branch develop" && exit 1; fi

# make sure no stale stuff before checkout
git reset --hard

# checkout release branch
echo "checkout ${RELEASE_BRANCH}"
git config --local remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"
git fetch --unshallow origin
git checkout -B "${RELEASE_BRANCH}" "origin/${RELEASE_BRANCH}"

# merge develop into release
NEW_VERSION="${RELEASE_TAG}.$(date +%Y%m%d%H%M)-r"
git merge --no-ff -X theirs --message "Release ${NEW_VERSION} [skip ci]" "origin/${GITHUB_BRANCH}"

# set version and add all changed POMs and add them to the merge commit
echo "changing version to ${NEW_VERSION}"
./mvnw org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion="${NEW_VERSION}" -Dtycho.mode=maven

# Generate and add CHANGELOG.md
echo "Generating CHANGELOG"
bundle exec github_changelog_generator \
    -t "${GITHUB_TOKEN}" \
    --since-tag 0.12.0.201101081422 \
    --no-verbose
git add CHANGELOG.md

# commit changes to branch (will be pushed after release)
git commit -a --amend --no-edit

# move tag to release branch
git tag -a -f "$RELEASE_TAG" -m "Release $RELEASE_TAG"

# checkout original branch
echo "checkout ${GITHUB_BRANCH}"
git checkout -B "${GITHUB_BRANCH}" "origin/${GITHUB_BRANCH}"

# merge back release branch to develop
git merge "${RELEASE_BRANCH}"

# reset options
set +eu
