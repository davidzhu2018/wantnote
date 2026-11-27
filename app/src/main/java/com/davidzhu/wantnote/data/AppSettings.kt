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

enum class SyncMethod(val label: String) {
    WEBDAV("WebDAV"),
    S3("S3"),
    ;

    companion object {
        fun fromStorage(value: String?): SyncMethod {
            return entries.firstOrNull { it.name == value } ?: WEBDAV
        }
    }
}

enum class EditorFontOption(val id: String, val label: String, val assetPath: String?) {
    SYSTEM("SYSTEM", "系统默认", null),
    NOTO_SANS_SC("NOTO_SANS_SC", "Noto Sans SC", "fonts/NotoSansSC-Regular.ttf"),
    NOTO_SERIF_SC("NOTO_SERIF_SC", "Noto Serif SC", "fonts/NotoSerifSC-Regular.ttf"),
    INTER("INTER", "Inter", "fonts/Inter-Regular.ttf"),
    JETBRAINS_MONO("JETBRAINS_MONO", "JetBrains Mono", "fonts/JetBrainsMono-Regular.ttf"),
    CUSTOM("CUSTOM", "自定义字体", null),
    ;

    companion object {
        fun fromStorage(value: String?): EditorFontOption {
            return entries.firstOrNull { it.id == value || it.name == value } ?: SYSTEM
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
    val aiSummaryProviderId: String = "",
    val aiSummaryModelId: String = "",
    val aiTitleTagProviderId: String = "",
    val aiTitleTagModelId: String = "",
    val aiSummaryText: String = "",
    val aiSummaryUpdatedAt: Long = 0L,
    val aiSummaryScope: String = "",
    val compressVideos: Boolean = false,
    val editorFontId: String = EditorFontOption.SYSTEM.id,
    val customFontPath: String = "",
    val syncMethod: String = SyncMethod.WEBDAV.name,
    /** 0.9.0：编辑器默认模式（所见即所得 / 源码）。 */
    val editorMode: String = EditorMode.WYSIWYG.name,
    /** 0.9.0：内置主题覆盖 + 用户自建主题，序列化为 JSON（见 ThemeCustoms）。 */
    val themeCustomJson: String = "{}",
    /** 0.9.0：Dinox Token 的可同步密文（AES-GCM，密钥派生自同步凭据，见 DinoxPortableCipher）。 */
    val dinoxTokenSealed: String = "",
    /** 0.9.0：dinoxTokenSealed 加密时使用的同步方式（WEBDAV/S3），供对端解密。 */
    val dinoxSealMethod: String = "",
    /** 0.9.0：上次 Dinox 增量同步的时间（epoch ms，ISO 8601 转换见 DinoxModels）。 */
    val dinoxLastSyncAt: Long = 0L,
    /** 0.9.0：Dinox 卡片盒名称过滤（空 = 全部；多个用「、/,」分隔）。 */
    val dinoxSyncBox: String = "",
    /** 0.9.0：Dinox 自动同步间隔分钟数（0 = 关闭）。 */
    val dinoxAutoSyncMinutes: Int = 0,
    /** 0.9.0：历史数据 → Markdown 的强制迁移是否已完成（完成后不再拦截启动）。 */
    val markdownMigrationDone: Boolean = false,
    /** 0.11.0：上次成功拉取 Dinox 卡片盒列表的时间（epoch ms，0 = 从未成功）。 */
    val dinoxBoxesFetchedAt: Long = 0L,
    /** 0.16.5：AI 能力选择开关（默认全部开启）。 */
    val aiEnableTitle: Boolean = true,
    val aiEnableTag: Boolean = true,
    val aiEnableBox: Boolean = true,
    val aiEnableSummary: Boolean = true,
    val aiEnableInsight: Boolean = true,
) {
    val palette: ThemePalette get() = ThemePalette.fromStorage(themeName)
    val fontScaleOption: FontScaleOption get() = FontScaleOption.fromStorage(fontScale)
    val dayNightMode: DayNightMode get() = DayNightMode.fromStorage(dayNight)
    val homeViewMode: HomeViewMode get() = HomeViewMode.fromStorage(homeView)
    val editorFont: EditorFontOption get() = EditorFontOption.fromStorage(editorFontId)
    val syncMethodOption: SyncMethod get() = SyncMethod.fromStorage(syncMethod)
    val editorModeOption: EditorMode get() = EditorMode.fromStorage(editorMode)

    companion object {
        val DEFAULT = AppSettings()
    }
}

enum class EditorMode(val label: String) {
    WYSIWYG("所见即所得"),
    SOURCE("源码"),
    ;

    companion object {
        fun fromStorage(value: String?): EditorMode {
            return entries.firstOrNull { it.name == value } ?: WYSIWYG
        }
    }
}
