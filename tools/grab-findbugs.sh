#!/bin/sh
url=http://findbugs.cs.umd.edu/eclipse/
eclipse=/opt/eclipse-indigo/eclipse/Eclipse.app/Contents/MacOS/eclipse
$eclipse -nosplash -verbose \
 -application org.eclipse.equinox.p2.artifact.repository.mirrorApplication \
 -verbose \
 -source $url \
 -destination file://$PWD/../com.basistech.m2e.code.quality.findbugs/findbugs.p2
