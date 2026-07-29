package com.musicapp.player.core.domain.model

enum class ColorSource {
    DYNAMIC,
    PRESET,
}

enum class PresetTheme {
    DEFAULT_BLUE,
    EMERALD_GREEN,
    SUNSET_ORANGE,
    VIOLET,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppLanguage {
    SYSTEM,
    SIMPLIFIED_CHINESE,
    ENGLISH,
}

enum class AeroMode {
    FLUID_MESH,
    GLOW_AURA,
    SOLID,
}

enum class ScanMode {
    ALL,
    SELECTED_DIRECTORIES,
}

data class AppSettings(
    val colorSource: ColorSource = ColorSource.DYNAMIC,
    val presetTheme: PresetTheme = PresetTheme.DEFAULT_BLUE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val aeroMode: AeroMode = AeroMode.GLOW_AURA,
    val fadeThroughDurationMs: Long = DEFAULT_FADE_THROUGH_DURATION_MS,
    val scanMode: ScanMode = ScanMode.ALL,
) {
    init {
        require(fadeThroughDurationMs in MIN_FADE_THROUGH_DURATION_MS..MAX_FADE_THROUGH_DURATION_MS) {
            "fadeThroughDurationMs must be between 0 and 2000 ms"
        }
        require(fadeThroughDurationMs % FADE_THROUGH_STEP_MS == 0L) {
            "fadeThroughDurationMs must use 250 ms steps"
        }
    }

    companion object {
        const val MIN_FADE_THROUGH_DURATION_MS: Long = 0
        const val MAX_FADE_THROUGH_DURATION_MS: Long = 2_000
        const val FADE_THROUGH_STEP_MS: Long = 250
        const val DEFAULT_FADE_THROUGH_DURATION_MS: Long = 500
    }
}
