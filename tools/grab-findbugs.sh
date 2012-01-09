#!/bin/sh
url=http://findbugs.cs.umd.edu/eclipse/
repo=file://$PWD/../com.basistech.m2e.code.quality.findbugs/findbugs.p2
eclipse=/opt/eclipse-indigo/eclipse/Eclipse.app/Contents/MacOS/eclipse
$eclipse -nosplash -verbose \
 -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
 -verbose \
 -source $url \
 -destination $repo

java -jar /opt/eclipse-indigo/eclipse/plugins/org.eclipse.equinox.launcher_1.2.0.v20110502.jar \
   -application org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher \
   -metadataRepository $repo \
   -artifactRepository $repo \
   -source ../com.basistech.m2e.code.quality.findbugs/findbugs.p2 \
   -configs gtk.linux.x86 \
   -publishArtifacts