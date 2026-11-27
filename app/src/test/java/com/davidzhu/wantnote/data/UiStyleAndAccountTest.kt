package com.davidzhu.wantnote.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 0.17.3：验证 UI 风格切换枚举（默认 MiuiX，支持 COUI）与账号管理配置字段。
 */
class UiStyleAndAccountTest {

    @Test
    fun uiStyleDefaultsToMiuixAndParsesCoui() {
        assertEquals(UiStyle.MIUIX, AppSettings.DEFAULT.uiStyleOption)
        assertEquals("MiuiX", UiStyle.MIUIX.label)
        assertEquals("COUI", UiStyle.COUI.label)
        assertEquals(UiStyle.COUI, UiStyle.fromStorage("COUI"))
        assertEquals(UiStyle.COUI, UiStyle.fromStorage("coui"))
        assertEquals(UiStyle.MIUIX, UiStyle.fromStorage("unknown"))
        assertEquals(UiStyle.MIUIX, UiStyle.fromStorage(null))
    }

    @Test
    fun accountProfileNicknamePreservedInAppSettings() {
        val updated = AppSettings.DEFAULT.copy(profileNickname = "想记测试用户", uiStyle = UiStyle.COUI.name)
        assertEquals("想记测试用户", updated.profileNickname)
        assertEquals(UiStyle.COUI, updated.uiStyleOption)
    }
}
