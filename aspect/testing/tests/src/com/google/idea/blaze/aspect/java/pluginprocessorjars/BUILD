load(
    "//aspect/testing/rules:intellij_aspect_test_fixture.bzl",
    "intellij_aspect_test_fixture",
)
load("//tools/build_defs/kotlin:rules.bzl", "kt_jvm_library")

licenses(["notice"])

java_library(
    name = "no_plugin_java",
    srcs = ["Foo.java"],
)

kt_jvm_library(
    name = "no_plugin_kt",
    srcs = ["Foo.kt"],
)

java_library(
    name = "has_plugin_java",
    srcs = ["Foo.java"],
    plugins = ["//java/com/google/android/apps/common/testing/tools/lint:ConfigShadowsLintCheckPlugin"],
)

kt_jvm_library(
    name = "has_plugin_kt",
    srcs = ["Foo.kt"],
    plugins = ["//java/com/google/android/apps/common/testing/tools/lint:ConfigShadowsLintCheckPlugin"],
)

java_library(
    name = "exported_plugins_java",
    srcs = ["Foo.java"],
    exported_plugins = ["//java/com/google/android/apps/common/testing/tools/lint:ConfigShadowsLintCheckPlugin"],
)

java_library(
    name = "has_plugin_deps_java",
    srcs = ["Foo.java"],
    deps = [":exported_plugins_java"],
)

kt_jvm_library(
    name = "exported_plugins_kt",
    srcs = ["Foo.kt"],
    exported_plugins = ["//java/com/google/android/apps/common/testing/tools/lint:ConfigShadowsLintCheckPlugin"],
)

kt_jvm_library(
    name = "has_plugin_deps_kt",
    srcs = ["Foo.kt"],
    deps = [":exported_plugins_kt"],
)

intellij_aspect_test_fixture(
    name = "no_plugin_fixture",
    deps = [
        ":no_plugin_java",
        ":no_plugin_kt",
    ],
)

intellij_aspect_test_fixture(
    name = "has_plugin_fixture",
    deps = [
        ":has_plugin_java",
        ":has_plugin_kt",
    ],
)

intellij_aspect_test_fixture(
    name = "has_plugin_deps_fixture",
    deps = [
        ":has_plugin_deps_java",
        ":has_plugin_deps_kt",
    ],
)

java_test(
    name = "PluginProcessorJarTest",
    srcs = ["PluginProcessorJarTest.java"],
    data = [
        ":has_plugin_deps_fixture",
        ":has_plugin_fixture",
        ":no_plugin_fixture",
    ],
    deps = [
        "//aspect/testing:BazelIntellijAspectTest",
        "//aspect/testing/rules:IntellijAspectTest",
        "//aspect/testing/rules:intellij_aspect_test_fixture_java_proto",
        "//intellij_platform_sdk:test_libs",
        "//proto:intellij_ide_info_java_proto",
        "@junit//jar",
    ],
)
