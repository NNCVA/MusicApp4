package com.musicapp.player.core.domain.model

import androidx.annotation.StringRes
import com.musicapp.player.R

data class BuiltInPresetDefinition(
    val index: Int,
    @param:StringRes val nameResId: Int,
    val defaultName: String,
    val bandLevelsMb: List<Int>,
)

object BuiltInEqualizerPresets {
    val FLAT = BuiltInPresetDefinition(
        index = 0,
        nameResId = R.string.equalizer_preset_flat,
        defaultName = "Flat",
        bandLevelsMb = listOf(0, 0, 0, 0, 0),
    )

    val BASS_BOOST = BuiltInPresetDefinition(
        index = 1,
        nameResId = R.string.equalizer_preset_bass_boost,
        defaultName = "Bass boost",
        bandLevelsMb = listOf(400, 200, -100, -200, 0),
    )

    val VOCAL = BuiltInPresetDefinition(
        index = 2,
        nameResId = R.string.equalizer_preset_vocal,
        defaultName = "Vocal",
        bandLevelsMb = listOf(-100, 0, 250, 300, 100),
    )

    val ROCK = BuiltInPresetDefinition(
        index = 3,
        nameResId = R.string.equalizer_preset_rock,
        defaultName = "Rock",
        bandLevelsMb = listOf(300, 0, -100, 200, 300),
    )

    val POP = BuiltInPresetDefinition(
        index = 4,
        nameResId = R.string.equalizer_preset_pop,
        defaultName = "Pop",
        bandLevelsMb = listOf(200, 100, 0, 200, 200),
    )

    val ELECTRONIC = BuiltInPresetDefinition(
        index = 5,
        nameResId = R.string.equalizer_preset_electronic,
        defaultName = "Electronic",
        bandLevelsMb = listOf(450, 0, -100, 200, 350),
    )

    val JAZZ = BuiltInPresetDefinition(
        index = 6,
        nameResId = R.string.equalizer_preset_jazz,
        defaultName = "Jazz",
        bandLevelsMb = listOf(100, 200, 0, 150, 200),
    )

    val CLASSICAL = BuiltInPresetDefinition(
        index = 7,
        nameResId = R.string.equalizer_preset_classical,
        defaultName = "Classical",
        bandLevelsMb = listOf(150, 0, 0, 50, 200),
    )

    val FOLK = BuiltInPresetDefinition(
        index = 8,
        nameResId = R.string.equalizer_preset_folk,
        defaultName = "Folk",
        bandLevelsMb = listOf(150, 100, 0, 100, -50),
    )

    val ALL_PRESETS: List<BuiltInPresetDefinition> = listOf(
        FLAT,
        BASS_BOOST,
        VOCAL,
        ROCK,
        POP,
        ELECTRONIC,
        JAZZ,
        CLASSICAL,
        FOLK,
    )

    fun toEqualizerPresets(): List<EqualizerPreset> {
        return ALL_PRESETS.map { def ->
            EqualizerPreset(
                index = def.index,
                name = def.defaultName,
                bandLevels = def.bandLevelsMb,
                nameResId = def.nameResId,
            )
        }
    }
}
