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
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.snood21.workprofilevpnswitcher"
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    data class ReleaseSigningCredentials(
        val keystorePath: String,
        val keystorePassword: String,
        val keyAlias: String,
        val keyPassword: String
    )

    val releaseSigningCredentials: ReleaseSigningCredentials? = run {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: localProperties["keystore.path"] as String?
        val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties["keystore.password"] as String?
        val signingKeyAlias = System.getenv("KEY_ALIAS") ?: localProperties["key.alias"] as String?
        val signingKeyPassword = System.getenv("KEY_PASSWORD") ?: localProperties["key.password"] as String?

        if (!keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
            !signingKeyAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()
        ) {
            ReleaseSigningCredentials(keystorePath, keystorePassword, signingKeyAlias, signingKeyPassword)
        } else {
            null
        }
    }

    signingConfigs {
        releaseSigningCredentials?.let { credentials ->
            create("release") {
                storeFile = file(credentials.keystorePath)
                storePassword = credentials.keystorePassword
                keyAlias = credentials.keyAlias
                keyPassword = credentials.keyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningCredentials != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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