package com.musicapp.player.core.domain

import com.musicapp.player.core.domain.policy.PlaylistNamePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaylistNamePolicyTest {
    @Test
    fun validNameTrimsDisplayAndBuildsAnNfcCaseInsensitiveKey() {
        val composed = PlaylistNamePolicy.validate("  CAF\u00c9  ") as PlaylistNamePolicy.Result.Valid
        val decomposed = PlaylistNamePolicy.validate("cafe\u0301") as PlaylistNamePolicy.Result.Valid

        assertEquals("CAF\u00c9", composed.displayName)
        assertEquals("caf\u00e9", composed.comparisonKey)
        assertEquals(composed.comparisonKey, decomposed.comparisonKey)
        assertEquals("caf\u00e9", decomposed.normalizedName)
    }

    @Test
    fun lengthIsCountedByUnicodeCodePointFromOneThroughFifty() {
        val supplementaryCharacter = "\uD83C\uDFB5"
        assertTrue(PlaylistNamePolicy.validate(supplementaryCharacter.repeat(50)) is PlaylistNamePolicy.Result.Valid)
        assertEquals(
            PlaylistNamePolicy.Result.Invalid(PlaylistNamePolicy.InvalidReason.TOO_LONG),
            PlaylistNamePolicy.validate(supplementaryCharacter.repeat(51)),
        )
    }

    @Test
    fun blankTrimmedNameIsInvalid() {
        assertEquals(
            PlaylistNamePolicy.Result.Invalid(PlaylistNamePolicy.InvalidReason.EMPTY),
            PlaylistNamePolicy.validate(" \t\n "),
        )
    }

    @Test
    fun comparisonKeyDoesNotDependOnTheDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val valid = PlaylistNamePolicy.validate("I") as PlaylistNamePolicy.Result.Valid

            assertEquals("i", valid.comparisonKey)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
