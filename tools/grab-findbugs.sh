#!/bin/sh

url=https://spotbugs.github.io/eclipse/
repo_path=../com.basistech.m2e.code.quality.findbugs/findbugs.p2
repo=file://$PWD/${repo_path}
eclipse=

# On MacOS X try to find the Eclipse application using the system
if [ -n "${OSTYPE}" ] && [[ ${OSTYPE} == darwin* ]] 
then
  eclipse_loc=`osascript -e 'tell application "System Events" to POSIX path of (file of process "Eclipse" as alias)'`
  eclipse=${eclipse_loc}/Contents/MacOS/eclipse
fi

# configure a default value
if [ -z "${eclipse}" ] 
then
  eclipse=/opt/eclipse-indigo/eclipse/Eclipse.app/Contents/MacOS/eclipse
fi

if [ \! -x "${eclipse}" ]
then
  echo Unabled to execute eclipse, please configure the path to eclipse
  exit 1
fi

if [ \! -d "$repo_path" ] 
then
  mkdir -p $repo_path
fi

$eclipse -nosplash -verbose \
 -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
 -verbose \
 -source $url \
 -destination $repo

$eclipse -nosplash -verbose \
 -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
 -metadataRepository $repo \
 -artifactRepository $repo \
 -source $PWD/${repo_path} \
 -configs gtk.linux.x86 \
 -publishArtifacts \
 -append
