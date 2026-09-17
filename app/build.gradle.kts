import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release signing is driven entirely by environment variables so that CI can inject them
 * from GitHub Actions secrets. Nothing secret is ever committed to this repository.
 *
 *   DSP_KEYSTORE_FILE      absolute path to the decoded keystore
 *   DSP_KEYSTORE_PASSWORD  keystore password
 *   DSP_KEY_ALIAS          key alias
 *   DSP_KEY_PASSWORD       key password
 *
 * When the keystore is absent (local dev, or a PR from a fork that cannot read secrets)
 * the release variant is simply left unsigned instead of failing the build.
 */
val keystorePath: String? = System.getenv("DSP_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystoreFile: File? = keystorePath?.let { file(it) }?.takeIf { it.exists() }
val hasReleaseSigning = keystoreFile != null

android {
    namespace = "com.dualshield.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dualshield.phone"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("DSP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DSP_KEY_ALIAS")
                keyPassword = System.getenv("DSP_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        /**
         * A release build in everything but signing, installable straight from CI.
         *
         * A debug APK is not a fair test of how this app feels: it is unshrunk, it runs with
         * `debuggable=true`, and Compose is dramatically slower under a debuggable process.
         * This variant is what testers should install when no release keystore is configured.
         */
        create("preview") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        lintConfig = file("lint.xml")
        // The build is lint-clean, so keep it that way: a new error-level finding should
        // stop CI rather than sit in a report nobody opens.
        abortOnError = true
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }
}

ksp {
    // Room schemas are checked in so that every future migration has a diffable baseline.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // Installs the bundled baseline profile so the startup path is AOT-compiled on first
    // run rather than interpreted until ART gets round to JIT-ing it.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Hard privacy gate.
 *
 * DualShieldPhone promises that it cannot talk to the network. The only way to prove that
 * for real is to inspect the *merged* manifest (ours plus every library's) after manifest
 * merging has run. This task fails the build if any network permission survived the merge.
 */
val forbiddenPermissions = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
)

androidComponents {
    onVariants { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercase() }
        val checkTask = tasks.register("check${capitalized}NoInternetPermission") {
            group = "verification"
            description = "Fails if a network permission leaked into the ${variant.name} merged manifest."
            val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
            inputs.file(manifest).withPropertyName("mergedManifest")
            val forbidden = forbiddenPermissions
            doLast {
                val text = manifest.get().asFile.readText()
                val leaked = forbidden.filter { text.contains("\"$it\"") }
                if (leaked.isNotEmpty()) {
                    throw GradleException(
                        buildString {
                            appendLine("Privacy check FAILED for variant '${variant.name}'.")
                            appendLine("DualShieldPhone must be fully offline, but the merged manifest declares:")
                            leaked.forEach { appendLine("  - $it") }
                            appendLine("Find the dependency that introduced it and remove it,")
                            appendLine("or strip the permission with tools:node=\"remove\" in AndroidManifest.xml.")
                        },
                    )
                }
                logger.lifecycle("Privacy check passed for '${variant.name}': no network permissions in merged manifest.")
            }
        }
        tasks.matching { it.name == "assemble$capitalized" || it.name == "bundle$capitalized" }
            .configureEach { dependsOn(checkTask) }
    }
}

tasks.register("checkNoInternetPermission") {
    group = "verification"
    description = "Runs the offline privacy gate for every variant."
    dependsOn(tasks.matching { it.name.startsWith("check") && it.name.endsWith("NoInternetPermission") && it.name != "checkNoInternetPermission" })
}
