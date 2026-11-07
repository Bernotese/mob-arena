#!/usr/bin/env bash
# Run Gradle with Java 25 in one of 3 shared build slots (max 3 builds at a time).
# Usage: tools/gradle.sh build   (from the repo or worktree root)
export JAVA_HOME=$HOME/.local/jdks/jdk-25.0.5+8 PATH=$HOME/.local/jdks/jdk-25.0.5+8/bin:$PATH
slots=3
for i in $(seq 1 $slots); do
  flock -n -E 75 "$HOME/.gradle/mobarena-slot-$i.lock" ./gradlew "$@"; rc=$?
  [ $rc -ne 75 ] && exit $rc
done
echo "All $slots build slots busy, waiting..." >&2
while true; do
  for i in $(seq 1 $slots); do
    flock -w 5 -E 75 "$HOME/.gradle/mobarena-slot-$i.lock" ./gradlew "$@"; rc=$?
    [ $rc -ne 75 ] && exit $rc
  done
done
