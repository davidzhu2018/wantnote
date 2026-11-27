import java.util.Properties

plugins {
    id("com.android.application")
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
    // 0.13.0：Miuix 0.9.4 / Compose 1.12 的 AAR 元数据要求 compileSdk ≥ 37。
    compileSdk = 37

    defaultConfig {
        applicationId = "com.davidzhu.wantnote"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "0.14.1"
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 0.13.0：Compose BOM 2026.08.00 = androidx Compose foundation 1.12.0，
    // 与 Miuix 0.9.4 依赖的 Compose Multiplatform foundation 1.12.0 对齐。
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 0.13.0：整套外观换成 Miuix（小米 HyperOS 风），固定最新稳定版 0.9.4。
    // 不引入 miuix-nav（导航仍是 androidx.navigation）与 miuix-blur（需 API 33）。
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 0.14.0：升级 activity-compose（及配套 core / lifecycle / navigation），为 Miuix 0.9.4
    // WindowDialog 注册 NavigationBackHandler 提供 NavigationEventDispatcherOwner，
    // 修复 0.13.0 详情页点击卡片盒名或标签行加号弹窗闪退。
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // OkHttp 可以发送任意 HTTP 动词（PROPFIND/MKCOL），避免
    // HttpURLConnection 在 doOutput 时把非标准方法悄悄改成 POST。
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Dinox 自动同步的周期性后台任务。
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    // JVM 单测里用真实的 org.json 实现（android.jar 里的 stub 不可用）。
    testImplementation("org.json:json:20240303")
}
