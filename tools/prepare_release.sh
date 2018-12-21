#!/bin/bash -eu

[ "$#" -ne "2" ] && echo "usage: $0 <new version> <release branch name>" && exit 1

# stop on first error
set -eu

# configure git
git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis CI"

# setup origin
git config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"

# verify same commit as branch
HEAD=$(git rev-parse --verify HEAD)
export GITHUB_BRANCH=$(git ls-remote -q --refs | grep $HEAD | awk '{print $2}' | sed -e 's/^refs\/heads\///')

C=$(echo $GITHUB_BRANCH | wc -w)
if [ "$C" -ne "1" ]; then echo "Tag cannot be resolved to branch name" && exit 1; fi

# make sure no stale stuff before checkout
git reset --hard

# checkout release branch
echo "checkout $2"
git fetch origin > /dev/null 2>&1
git checkout -B $2 origin/$2

# merge develop into release
git merge --no-ff -X theirs origin/$GITHUB_BRANCH

# set version and add all changed POMs and add them to the merge commit
echo "changing version to $1"
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$1 -Dtycho.mode=maven

# Install github_changelog_generator
gem install github_changelog_generator

# Generate and add CHANGELOG.md
echo "Generating CHANGELOG"
github_changelog_generator -t ${GITHUB_TOKEN} --since-tag 0.12.0.201101081422
git add CHANGELOG.md

# commit changes to branch (will be pushed after release)
git commit -a --amend --message "Release $1 [skip ci]" > /dev/null 2>&1

# Get latest (soon to be previous) release
previous_release_tag=$(curl -s \
    https://${GITHUB_TOKEN}@api.github.com/repos/${TRAVIS_REPO_SLUG}/releases/latest | \
        jq -r .tag_name)

# Create release notes
github_changelog_generator -t ${GITHUB_TOKEN} --no-unreleased --output /tmp/CHANGELOG-$1.md --since-tag ${previous_release_tag}
export RELEASE_CHANGELOG=$(< /tmp/CHANGELOG-$1.md)

# move tag to release branch
git tag -a -f $TRAVIS_TAG -m "Release $TRAVIS_TAG"

# checkout original branch
echo "checkout $GITHUB_BRANCH"
git checkout -B $GITHUB_BRANCH

# merge back release branch to develop
git merge $2

# reset options
set +eu
