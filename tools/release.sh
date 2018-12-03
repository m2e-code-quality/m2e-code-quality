#!/bin/bash -eu

[ "$#" -ne "1" ] && echo "usage: $0 <release branch name>" && exit 1

# move tag and push release branches
git push https://${GITHUB_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git :refs/tags/$TRAVIS_TAG
git push --quiet --atomic --tags https://${GITHUB_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git $1

# Overwrite CHANGELOG.md with JSON data for GitHub API
jq -n \
  --arg body "$RELEASE_CHANGELOG" \
  --arg name "Release $TRAVIS_TAG" \
  --arg tag_name "$TRAVIS_TAG" \
  --arg target_commitish "$1" \
  '{
    body: $body,
    name: $name,
    tag_name: $tag_name,
    target_commitish: $target_commitish,
    draft: false,
    prerelease: false
  }' > /tmp/release.json

# Create release in github
echo "Create release $TRAVIS_TAG for repo: $TRAVIS_REPO_SLUG, branch: $GITHUB_BRANCH"
upload_url=$(curl -s -H "Authorization: token $GITHUB_TOKEN" --data @/tmp/release.json "https://api.github.com/repos/${TRAVIS_REPO_SLUG}/releases" \
  | jq -r '.upload_url')

# upload p2 site to release
upload_url="${upload_url%\{*}"

FILES=./com.basistech.m2e.code.quality.site/target/com.basistech.m2e.code.quality.site-*.zip
for f in $FILES
do
  echo "uploading $f to $upload_url"
  curl -s -H "Authorization: token $GITHUB_TOKEN"  \
        -H "Content-Type: application/zip" \
        --data-binary @"$f"  \
        "$upload_url?name=$(basename $f)&label=P2%20Repository"
done

# increment version
NEW_VERSION=$(echo $TRAVIS_TAG | awk 'BEGIN { FS="." } { $3++; } { printf "%d.%d.%d\n", $1, $2, $3 }')-SNAPSHOT
echo "changing version to $NEW_VERSION"
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$NEW_VERSION -Dtycho.mode=maven

# commit the updated version
git commit -a --message "Next version: $NEW_VERSION" > /dev/null 2>&1

# push updated and development branche
git push --quiet https://${GITHUB_TOKEN}@github.com/${TRAVIS_REPO_SLUG}.git $GITHUB_BRANCH
