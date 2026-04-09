package com.example.arcomtechapp.util

import android.net.Uri

object WorkspaceLinks {
    fun normalizeUrl(url: String?): Uri? {
        if (url.isNullOrBlank()) return null
        val normalized = url.trim()
        val hasExplicitScheme = Regex("^[a-z][a-z\\d+\\-.]*://", RegexOption.IGNORE_CASE).containsMatchIn(normalized)
        if (!hasExplicitScheme && normalized.contains(":")) return null
        val withScheme = if (hasExplicitScheme) {
            normalized
        } else {
            "https://$normalized"
        }
        val parsed = Uri.parse(withScheme)
        val scheme = parsed.scheme?.lowercase()
        return if ((scheme == "http" || scheme == "https") && !parsed.host.isNullOrBlank()) parsed else null
    }

    fun configuredCount(vararg urls: String?): Int = urls.count { normalizeUrl(it) != null }
}
