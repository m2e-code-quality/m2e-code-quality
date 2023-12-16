# exclude-bugs-file

A test project that is configured to run the spotbugs:spotbugs goal on build
with a configured `exclude-bugs-file` property.
It should trigger only one SpotBugs marker - the other should be excluded (see file `baseline.xml`).
