package com.musicapp.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageControllerTest {
  @Test
  fun languageSelectionMapsToStableLocaleTags() {
    assertEquals("", AppLanguageController.languageTags(AppLanguage.FOLLOW_SYSTEM))
    assertEquals("zh-CN", AppLanguageController.languageTags(AppLanguage.SIMPLIFIED_CHINESE))
    assertEquals("en", AppLanguageController.languageTags(AppLanguage.ENGLISH))
  }
}
