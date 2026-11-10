package com.davidzhu.wantnote.sync

internal object Redacting {
    fun message(error: Throwable, secrets: List<String>): String {
        var text = error.message?.ifBlank { null } ?: error.javaClass.simpleName
        text = secrets.filter { it.length >= 4 }.fold(text) { acc, secret -> acc.replace(secret, "***") }
        text = text.replace(Regex("""(?i)(Authorization:\s*)\S+"""), "$1***")
        text = text.replace(Regex("""(?i)(AWSAccessKeyId=)[^&\s]+"""), "$1***")
        text = text.replace(Regex("""(?i)(X-Amz-Credential=)[^&\s]+"""), "$1***")
        return text
    }
}
