/*
 * From https://developer.android.com/build#top-level
 */

plugins {

    /**
     * Use `apply false` in the top-level build.gradle file to add a Gradle
     * plugin as a build dependency but not apply it to the current (root)
     * project. Don't use `apply false` in sub-projects. For more information,
     * see Applying external plugins with same version to subprojects.
     */

    /*
     * Android Studio generates these with alias(). Refer to the
     * libs.version.toml to see the id of the plugin.
     * Android recommends using the version catalog.
     * https://developer.android.com/build/dependencies
     */
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

