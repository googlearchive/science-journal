load("//java/com/google/android/apps/forscience/whistlepunk/builddefs:vars.bzl", "JAVACOPTS")
load("//tools/build_defs/android:rules.bzl", "android_library")

package(default_visibility = ["//java/com/google/android/apps/forscience:science_journal"])

licenses(["notice"])  # Apache License 2.0

SOURCES = glob(["*.java"])

android_library(
    name = "licenses",
    srcs = SOURCES,
    custom_package = "com.google.android.apps.forscience.whistlepunk.licenses",
    javacopts = JAVACOPTS,
)
