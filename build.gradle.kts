plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9 内置 Kotlin 支持；Kotlin 版本由 KGP 2.4.20 决定（compose 插件同版本）。
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
