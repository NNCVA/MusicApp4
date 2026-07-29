package com.musicapp.player.core.domain

import com.musicapp.player.core.domain.model.PathRule
import com.musicapp.player.core.domain.model.PathRuleId
import com.musicapp.player.core.domain.model.PathRuleKind
import com.musicapp.player.core.domain.model.ScanMode
import com.musicapp.player.core.domain.policy.PathRuleMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathRuleMatcherTest {
    private val rules = listOf(
        rule(1, "external_primary", "Music", PathRuleKind.INCLUDE),
        rule(2, "external_primary", "Music/Private", PathRuleKind.EXCLUDE),
    )

    @Test
    fun selectedDirectoriesRequireAnIncludeAndExcludeWins() {
        assertTrue(PathRuleMatcher.matches("external_primary", "Music/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
        assertFalse(PathRuleMatcher.matches("external_primary", "Music/Private/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
        assertFalse(PathRuleMatcher.matches("external_primary", "Podcasts/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
    }

    @Test
    fun allModeIgnoresIncludeRulesButStillAppliesExclusions() {
        assertTrue(PathRuleMatcher.matches("external_primary", "Podcasts/song.mp3", ScanMode.ALL, rules))
        assertFalse(PathRuleMatcher.matches("external_primary", "Music/Private/song.mp3", ScanMode.ALL, rules))
    }

    @Test
    fun rulesAreIsolatedByVolume() {
        assertFalse(PathRuleMatcher.matches("sdcard", "Music/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
        assertTrue(PathRuleMatcher.matches("sdcard", "Music/Private/song.mp3", ScanMode.ALL, rules))
    }

    @Test
    fun matchingUsesDirectorySegmentBoundariesAndNormalizedPaths() {
        assertFalse(PathRuleMatcher.matches("external_primary", "Musical/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
        assertTrue(PathRuleMatcher.matches("external_primary", "./Music\\Album/../song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
        assertFalse(PathRuleMatcher.matches("external_primary", "Music/Album/../Private/song.mp3", ScanMode.SELECTED_DIRECTORIES, rules))
    }

    @Test
    fun emptyDirectoryRuleCoversEveryPathWithinItsVolume() {
        val rootRule = rule(3, "external_primary", "", PathRuleKind.INCLUDE)

        assertTrue(
            PathRuleMatcher.matches(
                "external_primary",
                "Any/Nested/song.mp3",
                ScanMode.SELECTED_DIRECTORIES,
                listOf(rootRule),
            ),
        )
        assertFalse(
            PathRuleMatcher.matches(
                "sdcard",
                "Any/Nested/song.mp3",
                ScanMode.SELECTED_DIRECTORIES,
                listOf(rootRule),
            ),
        )
    }

    private fun rule(
        id: Long,
        volumeName: String,
        directory: String,
        kind: PathRuleKind,
    ) = PathRule(
        id = PathRuleId(id),
        volumeName = volumeName,
        directory = directory,
        kind = kind,
    )
}
