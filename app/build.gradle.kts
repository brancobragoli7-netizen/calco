plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cogito.calco"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cogito.calco"
        minSdk = 26
        // 34: la ventana no se dibuja debajo de las barras del sistema
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Clave propia incluida en el proyecto: así cada APK nuevo se instala
    // encima del anterior sin tener que desinstalar (y sin perder los paquetes).
    signingConfigs {
        create("propia") {
            storeFile = file("calco.jks")
            storePassword = "calco123"
            keyAlias = "calco"
            keyPassword = "calco123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("propia")
        }
        debug {
            signingConfig = signingConfigs.getByName("propia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
