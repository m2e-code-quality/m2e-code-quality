#!/bin/bash -eu

GIT_ROOT_DIR=$(cd `dirname $0` && echo `git rev-parse --show-toplevel`)
OLD_RELEASES_FILE=${GIT_ROOT_DIR}/tools/old_releases.list

CURRENT_SITE_FOLDER=current-site
SITE_GITHUB_BRANCH=gh-pages

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
  <children size='${countChildren}'>" > ${targetFolder}/compositeContent.xml
	for sd in $subdirs; do
		echo "    <child location='${sd}'/>" >> ${targetFolder}/compositeContent.xml
	done
	echo "</children>
</repository>
" >> ${targetFolder}/compositeContent.xml

	echo "<?xml version='1.0' encoding='UTF-8'?><?compositeArtifactRepository version='1.0.0'?>
<repository name='${SITE_NAME}' type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>
  <properties size='2'><property name='p2.timestamp' value='${now}'/><property name='p2.compressed' value='true'/></properties>
  <children size='${countChildren}'>" > ${targetFolder}/compositeArtifacts.xml
	for sd in $subdirs; do
		echo "    <child location='${sd}'/>" >> ${targetFolder}/compositeArtifacts.xml
	done
	echo "  </children>
</repository>
" >> ${targetFolder}/compositeArtifacts.xml
}

## -- fetch current site
rm -rf current-site
git clone https://github.com/${TRAVIS_REPO_SLUG}-p2-site.git -b ${SITE_GITHUB_BRANCH} ${CURRENT_SITE_FOLDER}

## -- integrate (copy) new version to the site
if [ ! -z "$TRAVIS_TAG" ]; then
  rm -rf ${CURRENT_SITE_FOLDER}/${TRAVIS_TAG} && mkdir ${CURRENT_SITE_FOLDER}/${TRAVIS_TAG} && cp -R ${NEW_SITE_FOLDER}/* ${CURRENT_SITE_FOLDER}/${TRAVIS_TAG}/
else
  rm -rf ${CURRENT_SITE_FOLDER}/snapshot && mkdir ${CURRENT_SITE_FOLDER}/snapshot && cp -R ${NEW_SITE_FOLDER}/* ${CURRENT_SITE_FOLDER}/snapshot/
fi

## -- regenerate composite meta data
STABLE_RELEASES="$(cat ${OLD_RELEASES_FILE}) $(find ${CURRENT_SITE_FOLDER}/* -maxdepth 1 -type d -name "[0-9]\.[0-9].[0-9]" -printf '%f\n')"
SNAPSHOT_RELEASES=$(find ${CURRENT_SITE_FOLDER}/snapshot* -maxdepth 1 -type d -name "[0-9]\.[0-9]\.[0-9]\.*" -printf '%f\n')

regenCompositeMetadata "${STABLE_RELEASES}" "${CURRENT_SITE_FOLDER}/"
