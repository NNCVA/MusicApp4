package com.musicapp.player.core.domain.policy

import java.text.Normalizer
import java.util.Locale

object PlaylistNamePolicy {
    const val MAX_CODE_POINTS: Int = 50

    fun validate(name: String): Result {
        val displayName = name.trim()
        val normalizedName = Normalizer.normalize(displayName, Normalizer.Form.NFC)
        val codePointCount = normalizedName.codePointCount(0, normalizedName.length)

        return when {
            codePointCount == 0 -> Result.Invalid(InvalidReason.EMPTY)
            codePointCount > MAX_CODE_POINTS -> Result.Invalid(InvalidReason.TOO_LONG)
            else -> Result.Valid(
                displayName = displayName,
                normalizedName = normalizedName,
                comparisonKey = normalizedName.lowercase(Locale.ROOT),
            )
        }
    }

    sealed interface Result {
        data class Valid(
            val displayName: String,
            val normalizedName: String,
            val comparisonKey: String,
        ) : Result

        data class Invalid(val reason: InvalidReason) : Result
    }

    enum class InvalidReason {
        EMPTY,
        TOO_LONG,
    }
}
