plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id ("com.google.gms.google-services")

}

android {
    namespace = "com.tambuchosecretdev.quickzenapp"
    
    // Forzar la versión de Google Play Services
    configurations.all {
        resolutionStrategy {
            force("com.google.android.gms:play-services-base:18.2.0")
        }
    }
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tambuchosecretdev.quickzenapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        // Habilitar desugaring para compatibilidad Java 8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-Xopt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-Xsuppress-version-warnings",
            "-opt-in=kotlin.RequiresOptIn",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=2.0.0"
        )
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    lint {
        abortOnError = false
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Desugaring para compatibilidad Java 8
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    
    // Google Play Services (especificar versión explícitamente)
    implementation("com.google.android.gms:play-services-base:18.2.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Firebase dependencies (utilizando versiones del BOM)
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")


    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.android.material:material:1.9.0")



    // Compose
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.ui.tooling.preview)
    debugImplementation(libs.ui.tooling)
    implementation(libs.androidx.activity.compose.v182)
    
    // Usar versión compatible con AGP 8.2.2 y compileSdk 34
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle - usar versiones compatibles con SDK 34
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Room
    val roomVersion = "2.6.1"
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Google Play Services - versiones compatibles y estables
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    // Ya está definido arriba: implementation("com.google.android.gms:play-services-base:18.2.0")
    implementation("com.google.android.gms:play-services-tasks:18.1.0")

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Google Sign-In (ya incluido arriba)


    // Otros
    // Eliminar dependencias credentials que requieren una versión de SDK no disponible
    // implementation(libs.androidx.credentials.v120alpha03)
    // implementation(libs.androidx.credentials.play.services.auth.v120alpha03)
    implementation(libs.androidx.datastore.preferences)
    
    // Usar versión compatible de Work Manager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    
    implementation(libs.coil.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.gson)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v121)
    androidTestImplementation(libs.androidx.espresso.core.v361)
}
