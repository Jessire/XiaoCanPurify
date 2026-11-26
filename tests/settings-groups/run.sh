#!/bin/sh
# Compile only the settings sources and run grouping tests; no Gradle, APK or device required.
set -eu
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
jdk=${JDK:-${JAVA_HOME:-/opt/dbp/jdk}}
android=${ANDROID_JAR:-/opt/dbp/sdk/platforms/android-34/android.jar}
source=app/src/main/java/io/github/jessire/xiaocanpurify
# LayoutSettings references these two members; no host hooks are invoked in this test.
cat > "$work/MainHook.java" <<'STUB'
package io.github.jessire.xiaocanpurify;
final class MainHook {
    static final String TARGET_PACKAGE = "test.host";
    static void log(String message) {}
}
STUB
"$jdk/bin/javac" --release 17 -encoding UTF-8 -cp "$android" -d "$work/classes" \
    "$source/LayoutSettings.java" "$source/SettingsGroups.java" "$source/SettingsUi.java" \
    "$work/MainHook.java" tests/settings-groups/SettingsGroupsTest.java
"$jdk/bin/java" -cp "$work/classes:$android" io.github.jessire.xiaocanpurify.SettingsGroupsTest
