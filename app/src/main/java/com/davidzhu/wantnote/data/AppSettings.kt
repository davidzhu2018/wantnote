package com.davidzhu.wantnote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ThemePalette(val label: String) {
    PAPER("纸色"),
    INK("墨色"),
    SAGE("青松"),
    ROSE("暮粉"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemePalette {
            return entries.firstOrNull { it.name == value } ?: PAPER
        }
    }
}

enum class DayNightMode(val label: String) {
    LIGHT("日间"),
    DARK("夜间"),
    SYSTEM("跟随系统"),
    ;

    companion object {
        fun fromStorage(value: String?): DayNightMode {
            return entries.firstOrNull { it.name == value } ?: LIGHT
        }
    }
}

enum class FontScaleOption(val label: String, val scale: Float) {
    SMALL("小", 0.85f),
    STANDARD("标准", 1.0f),
    LARGE("大", 1.15f),
    XLARGE("特大", 1.3f),
    ;

    companion object {
        fun fromStorage(value: String?): FontScaleOption {
            return entries.firstOrNull { it.name == value } ?: STANDARD
        }
    }
}

enum class HomeViewMode(val label: String) {
    CARD("卡片"),
    WATERFALL("瀑布流"),
    MINIMAL("极简"),
    TIMESTAMP("时间戳"),
    ;

    companion object {
        fun fromStorage(value: String?): HomeViewMode {
            return entries.firstOrNull { it.name == value } ?: CARD
        }
    }
}

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val themeName: String = ThemePalette.PAPER.name,
    val fontScale: String = FontScaleOption.STANDARD.name,
    val dayNight: String = DayNightMode.LIGHT.name,
    val compressImages: Boolean = true,
    val homeView: String = HomeViewMode.CARD.name,
    val webdavUrl: String = "",
    val webdavUsername: String = "",
    val webdavPassword: String = "",
    val s3Endpoint: String = "",
    val s3Bucket: String = "",
    val s3Region: String = "",
    val s3AccessKey: String = "",
    val s3SecretKey: String = "",
    val s3Prefix: String = "",
) {
    val palette: ThemePalette get() = ThemePalette.fromStorage(themeName)
    val fontScaleOption: FontScaleOption get() = FontScaleOption.fromStorage(fontScale)
    val dayNightMode: DayNightMode get() = DayNightMode.fromStorage(dayNight)
    val homeViewMode: HomeViewMode get() = HomeViewMode.fromStorage(homeView)

    companion object {
        val DEFAULT = AppSettings()
    }
}
