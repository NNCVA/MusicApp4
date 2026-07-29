package com.musicapp.player.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppLanguageController {
  fun languageTags(language: AppLanguage): String =
    when (language) {
      AppLanguage.FOLLOW_SYSTEM -> ""
      AppLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
      AppLanguage.ENGLISH -> "en"
    }

  fun apply(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(
      LocaleListCompat.forLanguageTags(languageTags(language)),
    )
  }
}
