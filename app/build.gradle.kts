plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads app/google-services.json and generates the Firebase config
    // resources. That file comes from the Firebase console (Project settings →
    // Your apps → Android) and is NOT in the repo — the build fails with
    // "File google-services.json is missing" until it is added.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.mobile_app_herp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.mobile_app_herp"
        minSdk = 24
        targetSdk = 36
        // Overridable from CI: -PappVersionCode=42 -PappVersionName=1.2.0
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0"

        // owner/repo the in-app updater checks for new releases.
        val githubRepo = (project.findProperty("githubRepo") as String?) ?: "OWNER/REPO"
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Populated on CI from secrets; local builds fall back to the debug key.
            System.getenv("KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    /**
     * Which deployment the app talks to. Hosts are {slug}-{module}.{domainBase},
     * so this one string decides the API, the IdP and the address shown at login.
     *
     * Dev and prod are both served from this domain and separated by workspace,
     * not by hostname — so a debug build reaches exactly the same servers as a
     * release one. Point a build somewhere else with -PdomainBase=…
     */
    val domainBase = (project.findProperty("domainBase") as String?) ?: "hotel-erp.ceyinfo.com"

    buildTypes {
        debug {
            buildConfigField("String", "DOMAIN_BASE", "\"$domainBase\"")
            // Declared, never inferred from the domain: both environments share
            // one hostname, so comparing it could not tell them apart. This says
            // which BUILD you are holding, which is what support needs to know.
            buildConfigField("boolean", "IS_PRODUCTION", "false")
        }
        release {
            buildConfigField("String", "DOMAIN_BASE", "\"$domainBase\"")
            buildConfigField("boolean", "IS_PRODUCTION", "true")
            optimization {
                enable = false
            }
            // Every published APK must be signed with the SAME key or Android
            // refuses the update with a signature mismatch. Debug-signed local
            // builds are fine to run, they just can't upgrade a real install.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    // Versions come from the BoM — never pin a Firebase artifact individually.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}