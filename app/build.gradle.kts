import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.snood21.workprofilevpnswitcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.snood21.workprofilevpnswitcher"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: localProperties["keystore.path"] as String)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties["keystore.password"] as String
            keyAlias = System.getenv("KEY_ALIAS") ?: localProperties["key.alias"] as String
            keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties["key.password"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val versionName = output.versionName.orNull ?: "unknown"
                output.outputFileName.set(
                    "${rootProject.name}-${versionName}-${variant.buildType}.apk"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}