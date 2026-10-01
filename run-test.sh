#!/usr/bin/env sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/appointment-agent-tracking-test-classes"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print0 | xargs -0 javac -d "$BUILD_DIR"
java -cp "$BUILD_DIR" dev.clinic.agent.workflow.AppointmentAgentLoopTest
