import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 正式 keystore 只通过环境变量或 local.properties 传入，文件本身不进 git、
// 不进出包目录。四项齐全才接线 release 签名；缺任何一项时 release 保持不
// 签名，绝不回退到会漂移的临时 debug 证书充当「正式签名」。
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(envName: String, propName: String): String? {
    val fromEnv = System.getenv(envName)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromProp = keystoreProps.getProperty(propName)
    if (!fromProp.isNullOrBlank()) return fromProp
    return null
}
val releaseStoreFile = signingValue("WANTNOTE_STORE_FILE", "wantnote.storeFile")
val releaseStorePassword = signingValue("WANTNOTE_STORE_PASSWORD", "wantnote.storePassword")
val releaseKeyAlias = signingValue("WANTNOTE_KEY_ALIAS", "wantnote.keyAlias")
val releaseKeyPassword = signingValue("WANTNOTE_KEY_PASSWORD", "wantnote.keyPassword")
val hasExternalKeystore = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.davidzhu.wantnote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.davidzhu.wantnote"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
    }

    signingConfigs {
        if (hasExternalKeystore) {
            create("externalRelease") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 只在有正式 keystore 时签名；否则保持未签名（不使用 debug 证书）。
            signingConfigs.findByName("externalRelease")?.let { signingConfig = it }
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    ksp("androidx.room:room-compiler:2.6.1")
}
