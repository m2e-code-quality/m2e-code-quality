#!/bin/bash -eu

[ "$#" -ne "1" ] && echo "usage: $0 <new version>" && exit 1

DIR=$(cd `dirname $0` && echo `git rev-parse --show-toplevel`)

(cd $DIR && mvn org.eclipse.tycho:tycho-versions-plugin:set-version -DnewVersion=$1 -Dtycho.mode=maven)
