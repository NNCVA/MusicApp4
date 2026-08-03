package com.musicapp.player.core.domain.policy

import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.ScanMode

object PathRuleMatcher {
    fun matches(
        volumeName: String,
        path: String,
        scanMode: ScanMode,
        rules: Collection<PathRule>,
    ): Boolean {
        val normalizedPath = normalizePath(path)
        val volumeRules = rules.filter { it.volumeName == volumeName }

        if (volumeRules.any { it.kind == PathRuleKind.EXCLUDE && contains(it.directory, normalizedPath) }) {
            return false
        }

        return scanMode == ScanMode.ALL ||
            volumeRules.any { it.kind == PathRuleKind.INCLUDE && contains(it.directory, normalizedPath) }
    }

    private fun contains(directory: String, normalizedPath: String): Boolean {
        val normalizedDirectory = normalizePath(directory)
        return normalizedDirectory.isEmpty() ||
            normalizedPath == normalizedDirectory ||
            normalizedPath.startsWith("$normalizedDirectory/")
    }

    private fun normalizePath(path: String): String {
        val segments = ArrayDeque<String>()
        path.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }
}
