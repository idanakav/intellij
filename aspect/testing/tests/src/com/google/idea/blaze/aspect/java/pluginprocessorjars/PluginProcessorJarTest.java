/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.aspect.java.pluginprocessorjars;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import com.google.idea.blaze.aspect.IntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests plugin processor jars from java and kotlin libraries. */
@RunWith(JUnit4.class)
public class PluginProcessorJarTest extends BazelIntellijAspectTest {
  @Test
  public void testPluginProcessorJarNotCreatedForNoPluginRules() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":no_plugin_fixture");

    // verify it works both java and kotlin rules
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":no_plugin_java");
    assertThat(targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList()).isEmpty();
    targetIdeInfo = findTarget(testFixture, ":no_plugin_kt");
    assertThat(targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList()).isEmpty();
  }

  @Test
  public void testPluginProcessorJarCreatedForPluginRules() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin_java");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .contains(
            jarString(
                "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar",
                null,
                null));

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(
            "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar");

    targetIdeInfo = findTarget(testFixture, ":has_plugin_kt");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .contains(
            jarString(
                "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar",
                null,
                null));

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(
            "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar");
  }

  @Test
  public void testPluginProcessorJarCreatedForPluginDepsRules() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":has_plugin_deps_fixture");
    TargetIdeInfo targetIdeInfo = findTarget(testFixture, ":has_plugin_deps_java");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .contains(
            jarString(
                "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar",
                null,
                null));

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(
            "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar");

    targetIdeInfo = findTarget(testFixture, ":has_plugin_deps_kt");

    assertThat(
            targetIdeInfo.getJavaIdeInfo().getPluginProcessorJarsList().stream()
                .map(IntellijAspectTest::libraryArtifactToString)
                .collect(toList()))
        .contains(
            jarString(
                "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar",
                null,
                null));

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-java"))
        .contains(
            "java/com/google/android/apps/common/testing/tools/lint/libConfigShadowsLintCheck.jar");
  }
}
