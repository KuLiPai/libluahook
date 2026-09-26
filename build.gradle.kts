// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all library modules to the local Maven repository."

    dependsOn(subprojects.map { "${it.path}:publishToMavenLocal" })
}
