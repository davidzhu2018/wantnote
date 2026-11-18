@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.davidzhu.wantnote.sync.dinox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Dinox Token 的本机加密存储：Android Keystore 里的 AES-256-GCM 密钥
 * （不出设备、不可导出）加密后存进应用私有 SharedPreferences。
 *
 * - prefs 文件里只有密文（IV + GCM 密文，Base64），没有明文 Token。
 * - 密钥被系统清除（如清除凭据）时自动重新生成并使旧密文失效，
 *   load() 返回 null，用户重新粘贴即可。
 * - Token 不进日志/崩溃文本：本类不打印任何内容，调用方只拿到布尔/异常。
 */
class DinoxTokenVault(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasToken(): Boolean = load() != null

    /** 保存并加密 Token；失败抛 [DinoxApiException]（不含敏感信息）。 */
    fun save(token: String) {
        val clean = token.trim()
        require(clean.isNotEmpty()) { "Token 不能为空" }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(iv.size + encrypted.size)
        iv.copyInto(payload)
        encrypted.copyInto(payload, iv.size)
        prefs.edit().putString(KEY_SEALED, encodeBase64(payload)).apply()
    }

    /** 解密出明文 Token；未保存 / 密钥失效 / 解密失败返回 null。 */
    fun load(): String? {
        val stored = prefs.getString(KEY_SEALED, null) ?: return null
        val payload = runCatching { decodeBase64(stored) }.getOrNull() ?: return null
        if (payload.size <= GCM_IV_BYTES) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, GCM_IV_BYTES)),
            )
            String(cipher.doFinal(payload.copyOfRange(GCM_IV_BYTES, payload.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    /** 清除本机 Token（密文与 keystore 别名都清掉）。 */
    fun clear() {
        prefs.edit().remove(KEY_SEALED).apply()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encodeBase64(data: ByteArray): String {
        return kotlin.io.encoding.Base64.Default.encode(data)
    }

    private fun decodeBase64(text: String): ByteArray {
        return kotlin.io.encoding.Base64.Default.decode(text)
    }

    companion object {
        private const val PREFS_NAME = "wantnote_dinox_secure"
        private const val KEY_SEALED = "dinox_token_sealed"
        private const val KEY_ALIAS = "wantnote_dinox_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
