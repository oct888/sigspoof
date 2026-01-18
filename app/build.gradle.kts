import com.android.ide.common.signing.KeystoreHelper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.PrintStream
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
keystoreProperties.load(keystorePropertiesFile.inputStream())

android {
    namespace = "com.oct.sigspoof"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.oct.sigspoof"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Skip signature verification in debug builds
            buildConfigField("boolean", "SKIP_SIGNATURE_VERIFICATION", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "SKIP_SIGNATURE_VERIFICATION", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    signingConfigs {
        create("release"){
            storeFile = keystoreProperties["signing.storeFile"]?.let { file(it) } ?: signingConfigs.getByName("debug").storeFile
            storePassword = keystoreProperties["signing.storePassword"] as String? ?: "android"
            keyAlias = keystoreProperties["signing.keyAlias"] as String? ?: "androiddebugkey"
            keyPassword = keystoreProperties["signing.keyPassword"] as String? ?: "android"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

// Generate signing certificate bytes at build time for signature verification
afterEvaluate {
    android.applicationVariants.forEach { variant ->
        val variantCapped = variant.name.replaceFirstChar { it.titlecase(Locale.ROOT) }
        val variantLowered = variant.name.lowercase(Locale.ROOT)

        val outSrcDir = layout.buildDirectory.dir("generated/source/signInfo/${variantLowered}")
        val outSrc = outSrcDir.get().file("com/oct/sigspoof/SigningCert.java")
        val signInfoTask = tasks.register("generate${variantCapped}SignInfo") {
            outputs.file(outSrc)
            doLast {
                val sign = variant.signingConfig
                outSrc.asFile.parentFile.mkdirs()
                val certificateInfo = KeystoreHelper.getCertificateInfo(
                    sign?.storeType,
                    sign?.storeFile,
                    sign?.storePassword,
                    sign?.keyPassword,
                    sign?.keyAlias
                )
                PrintStream(outSrc.asFile).apply {
                    println("package com.oct.sigspoof;")
                    println("public final class SigningCert {")
                    print("public static final byte[] CERT_BYTES = {")
                    val bytes = certificateInfo.certificate.encoded
                    print(bytes.joinToString(",") { it.toString() })
                    println("};")
                    println("}")
                }
            }
        }
        variant.registerJavaGeneratingTask(signInfoTask, outSrcDir.get().asFile)

        val kotlinCompileTask = tasks.findByName("compile${variantCapped}Kotlin") as? KotlinCompile
        kotlinCompileTask?.let {
            it.dependsOn(signInfoTask)
            val srcSet = objects.sourceDirectorySet("signInfo", "signInfo").srcDir(outSrcDir)
            it.source(srcSet)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation(libs.gson)
    
    // Xposed API (compile-only, provided by framework at runtime)
    compileOnly("de.robv.android.xposed:api:82")
    
    // Hidden API compatibility for system calls
    implementation("dev.rikka.hidden:compat:4.3.3")
    compileOnly("dev.rikka.hidden:stub:4.3.3")
    
    // APK signature verification
    implementation("com.android.tools.build:apksig:8.2.0")
}