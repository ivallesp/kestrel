// Declaring every plugin here (apply false) keeps one shared build classpath.
// The Compose compiler plugin pulls kotlin-gradle-plugin at the same version,
// which lifts AGP 9's bundled KGP (2.2.10) to the catalog's Kotlin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// Spotless presets ktlint_code_style=intellij_idea and that preset beats .editorconfig,
// so the code style is set here.
val ktlintStyle = mapOf("ktlint_code_style" to "ktlint_official")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", ".claude/**")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(ktlintStyle)
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRulesKtlint.get()}"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", ".claude/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintStyle)
    }
}
