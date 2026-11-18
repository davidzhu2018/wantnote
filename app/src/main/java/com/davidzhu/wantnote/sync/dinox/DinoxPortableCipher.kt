@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.davidzhu.wantnote.sync.dinox

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dinox Token 的「可同步密文」封装（纯 JVM，可单测）。
 *
 * 背景：Token 在本机用 Android Keystore 加密落盘（[DinoxTokenVault]），
 * 但 Keystore 密钥不出设备 —— 直接把本机密文放进 WebDAV/S3 设置同步，
 * 另一台设备解不开。所以跨设备传输用的是第二层封装：密钥派生自当前
 * WebDAV 密码 / S3 SecretKey（对端恢复设置后本来就持有同一凭据），
 * AES-256-GCM 认证加密。凭据不同的设备解密失败 → 静默放弃，用户重新
 * 粘贴一次即可。
 *
 * 明文 Token 永远不进仓库、日志、崩溃上报或未加密的 prefs/DB。
 */
object DinoxPortableCipher {
    /** 密文格式标记（算法升级时递增）。 */
    private const val PREFIX = "WN1:"

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_INFO = "wantnote-dinox-portable-v1"

    /**
     * 用来派生密钥的同步凭据：与设置同步走的是同一通道，
     * 对端只有在恢复出同样的凭据后才能解密。
     */
    fun syncSecret(syncMethod: String, webdavPassword: String, s3SecretKey: String): String {
        return when (syncMethod.uppercase()) {
            "S3" -> s3SecretKey
            else -> webdavPassword
        }
    }

    /** 加密；凭据为空（用户未配置云同步）返回 null，表示无可同步密文。 */
    fun seal(token: String, secret: String): String? {
        if (token.isBlank() || secret.isBlank()) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(secret), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + encrypted.size)
        iv.copyInto(payload)
        encrypted.copyInto(payload, iv.size)
        return PREFIX + encodeBase64(payload)
    }

    /** 解密；密文格式不对或凭据不匹配返回 null（绝不抛异常）。 */
    fun open(sealed: String, secret: String): String? {
        if (sealed.isBlank() || secret.isBlank()) return null
        if (!sealed.startsWith(PREFIX)) return null
        val payload = runCatching { decodeBase64(sealed.removePrefix(PREFIX)) }.getOrNull() ?: return null
        if (payload.size <= GCM_IV_BYTES) return null
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(secret), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val key = digest.digest((KEY_INFO + secret).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(key, "AES")
    }

    // kotlin.io.encoding.Base64 在 JVM 与 Android 上行为一致，且不依赖 API 26。
    private fun encodeBase64(data: ByteArray): String {
        return kotlin.io.encoding.Base64.Default.encode(data)
    }

    private fun decodeBase64(text: String): ByteArray {
        return kotlin.io.encoding.Base64.Default.decode(text)
    }
}
