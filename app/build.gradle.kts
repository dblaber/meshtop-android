import java.text.SimpleDateFormat
import java.util.Date

fun gitExec(vararg args: String): String = try {
    val process = ProcessBuilder(*args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim()
        .also { process.waitFor() }
} catch (_: Exception) { "" }

// "v1.2.3" on a tag, "v1.2.3-5-gabcdef" between tags, commit hash if no tags
val gitVersionName = gitExec("git", "describe", "--tags", "--always").ifEmpty { "dev" }
// Count all reachable commits for a monotonically increasing versionCode
val gitVersionCode = gitExec("git", "rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val gitCommitHash = gitExec("git", "rev-parse", "--short", "HEAD").ifEmpty { "unknown" }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.protobuf")
}

android {
    namespace = "com.meshtop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshtop"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName
        buildConfigField("String", "BUILD_DATE", "\"${SimpleDateFormat("yyyy-MM-dd").format(Date())}\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitHash\"")

    }

    signingConfigs {
        create("release") {
            val props = rootProject.file("keystore.properties")
            if (props.exists()) {
                val lines = props.readLines().associate { line ->
                    val (k, v) = line.split("=", limit = 2)
                    k.trim() to v.trim()
                }
                storeFile = file(lines["storeFile"]!!)
                storePassword = lines["storePassword"]!!
                keyAlias = lines["keyAlias"]!!
                keyPassword = lines["keyPassword"]!!
            } else {
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "keystore.jks")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
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
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.28.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // Protobuf
    implementation("com.google.protobuf:protobuf-javalite:4.28.3")

    // MQTT
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // PostgreSQL JDBC (optional DB node name loading) - 42.2.5 is Android-compatible
    implementation("org.postgresql:postgresql:42.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
