package com.musicapp.player

import com.musicapp.player.core.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun languageOptionsMapToPlatformLocaleTags() {
        assertEquals("", AppLanguage.SYSTEM.languageTags())
        assertEquals("zh-CN", AppLanguage.SIMPLIFIED_CHINESE.languageTags())
        assertEquals("en", AppLanguage.ENGLISH.languageTags())
    }
}
