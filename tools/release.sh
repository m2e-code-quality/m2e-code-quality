#!/bin/bash -eu

if [[ "${GITHUB_REF}" != refs/tags/* ]]; then
    echo "${GITHUB_REF} is not a tag!"
    exit 1
fi

# stop on first error
set -eu

RELEASE_TAG=${GITHUB_REF##refs/tags/}
RELEASE_BRANCH=master
REPOSITORY=m2e-code-quality/m2e-code-quality
GITHUB_BRANCH=develop

# remove remote tag and push moved tag + release branch
git push origin ":refs/tags/$RELEASE_TAG"
git push --atomic --tags origin $RELEASE_BRANCH

# Overwrite CHANGELOG.md with JSON data for GitHub API
# extract the release notes
END_LINE=$(grep -n "^## " CHANGELOG.md|head -2|tail -1|cut -d ":" -f 1)
END_LINE=$((END_LINE - 1))
RELEASE_CHANGELOG="$(head -$END_LINE CHANGELOG.md)"

request="$(jq -n \
  --arg body "$RELEASE_CHANGELOG" \
  --arg name "Release $RELEASE_TAG" \
  --arg tag_name "$RELEASE_TAG" \
  --arg target_commitish "$RELEASE_BRANCH" \
  '{
    body: $body,
    name: $name,
    tag_name: $tag_name,
    target_commitish: $target_commitish,
    draft: false,
    prerelease: false
  }')"

# Create release in github
echo "Create release $RELEASE_TAG for repo: $REPOSITORY, branch: $GITHUB_BRANCH"
upload_url=$(curl -s -H "Authorization: token $GITHUB_TOKEN" --data "${request}" "https://api.github.com/repos/${REPOSITORY}/releases" \
  | jq -r '.upload_url')

# upload p2 site to release
upload_url="${upload_url%\{*}"

FILES=(./com.basistech.m2e.code.quality.site/target/com.basistech.m2e.code.quality.site-*.zip)
for f in "${FILES[@]}"
do
  echo "uploading $f to $upload_url"
  curl -s -H "Authorization: token $GITHUB_TOKEN"  \
        -H "Content-Type: application/zip" \
        --data-binary @"$f"  \
        "$upload_url?name=$(basename "$f")&label=P2%20Repository"
done

# increment version
NEW_VERSION=$(echo "$RELEASE_TAG" | awk 'BEGIN { FS="." } { $3++; } { printf "%d.%d.%d\n", $1, $2, $3 }')-SNAPSHOT
echo "changing version to $NEW_VERSION"
mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion="$NEW_VERSION" -Dtycho.mode=maven

# commit the updated version
git commit -a --message "Next version: $NEW_VERSION"

# push updated development branch
git push "$GITHUB_BRANCH"
