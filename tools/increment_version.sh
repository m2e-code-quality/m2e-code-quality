#!/bin/bash -eu

[ "$#" -ne "1" ] && echo "usage: $0 <current version>" && exit 1

DIR=$(cd `dirname $0` && echo `git rev-parse --show-toplevel`)
NEW_VERSION=$(echo $1 | awk 'BEGIN { FS="." } { $3++; } { printf "%d.%d.%d\n", $1, $2, $3 }')-SNAPSHOT

cd $DIR

git config --global user.email "travis@travis-ci.org"
git config --global user.name "Travis CI"

# make sure no stale stuff before checkout
git reset --hard

# setup origin
git remote set-url origin https://${GITHUB_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git

# verify same commit as branch
HEAD=$(git rev-parse --verify HEAD)
GITHUB_BRANCH=$(git ls-remote -q --refs | grep $HEAD | awk '{print $2}' | sed -e 's/^refs\/heads\///')

C=$(echo $GITHUB_BRANCH | wc -w)
if [ "$C" -ne "1" ]; then echo "Tag cannot be resolved to branch name" && exit 1; fi

# checkout branch
echo "checkout $GITHUB_BRANCH"
git fetch origin "+refs/heads/$GITHUB_BRANCH:refs/remotes/origin/$GITHUB_BRANCH" > /dev/null 2>&1
git checkout -B $GITHUB_BRANCH

# increment version
echo "changing version to $NEW_VERSION"
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$NEW_VERSION -Dtycho.mode=maven

# push changes to branch
git commit -a --message "Next version: $NEW_VERSION" > /dev/null 2>&1
git push origin --quiet $GITHUB_BRANCH
