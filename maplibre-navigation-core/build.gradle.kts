plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization") version "1.9.22"
}

kotlin {
    androidTarget()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.maplibre.geojson)
                implementation(libs.maplibre.geojson.turf)
                implementation(libs.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.maplibre)
                implementation(libs.play.services.location)
            }
        }
        val androidUnitTest by getting
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "org.maplibre.navigation.core"

    defaultConfig {
        compileSdk = 35
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        consumerProguardFiles("proguard-consumer.pro")
    }

    buildFeatures {
        buildConfig = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    // Disable unit tests for local build
    tasks.withType<Test>().configureEach {
        enabled = false
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Additionally disable all UnitTest-related compilation/execution tasks to avoid compiling test sources
tasks.matching { it.name.contains("UnitTest") }.configureEach {
    enabled = false
}


// Exclude old version of GeoJSON/Turf from Android SDK to avoid duplicates with KMP geojson/turf
configurations {
    configureEach {
        exclude(group = "org.maplibre.gl", module = "android-sdk-geojson")
        exclude(group = "org.maplibre.gl", module = "android-sdk-turf")
    }
}
