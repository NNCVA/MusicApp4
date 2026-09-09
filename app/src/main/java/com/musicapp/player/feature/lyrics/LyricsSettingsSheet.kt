package com.musicapp.player.feature.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.InsetPillSlider
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSettingsSheet(
    fontSizeSp: Int,
    isTextCentered: Boolean,
    fontWeight: Int,
    onFontSizeChange: (Int) -> Unit,
    onTextCenteredChange: (Boolean) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = MusicTheme.dimensions.windowWidthTier == MusicWindowWidthTier.COMPACT
    if (compact) {
        ModalBottomSheet(
            modifier = modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            containerColor = MusicTheme.colors.surface,
            dragHandle = null,
            shape = MusicTheme.shapes.extraLarge,
        ) {
            LyricsSettingsContent(
                fontSizeSp = fontSizeSp,
                isTextCentered = isTextCentered,
                fontWeight = fontWeight,
                onFontSizeChange = onFontSizeChange,
                onTextCenteredChange = onTextCenteredChange,
                onFontWeightChange = onFontWeightChange,
            )
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                shape = MusicTheme.shapes.extraLarge,
                color = MusicTheme.colors.surface,
            ) {
                LyricsSettingsContent(
                    fontSizeSp = fontSizeSp,
                    isTextCentered = isTextCentered,
                    fontWeight = fontWeight,
                    onFontSizeChange = onFontSizeChange,
                    onTextCenteredChange = onTextCenteredChange,
                    onFontWeightChange = onFontWeightChange,
                )
            }
        }
    }
}

@Composable
internal fun LyricsSettingsContent(
    fontSizeSp: Int,
    isTextCentered: Boolean,
    fontWeight: Int,
    onFontSizeChange: (Int) -> Unit,
    onTextCenteredChange: (Boolean) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = dimensions.contentHorizontalPadding,
                end = dimensions.contentHorizontalPadding,
                top = dimensions.spaceLarge,
                bottom = dimensions.spaceExtraLarge,
            ),
    ) {
        Text(
            text = stringResource(R.string.lyrics_settings_title),
            style = MusicTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MusicTheme.colors.onSurface,
        )
        Spacer(Modifier.height(dimensions.spaceLarge))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MusicTheme.shapes.large,
            color = MusicTheme.aeroCardContainerColor,
            contentColor = MusicTheme.colors.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensions.contentHorizontalPadding,
                        vertical = dimensions.spaceMedium,
                    ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
            ) {
                // Item 1: Font Size
                LyricsFontSizeSection(
                    fontSizeSp = fontSizeSp,
                    onFontSizeChange = onFontSizeChange,
                )

                // Item 2: Text Center Alignment
                LyricsTextCenterSection(
                    isTextCentered = isTextCentered,
                    onTextCenteredChange = onTextCenteredChange,
                )

                // Item 3: Font Weight
                LyricsFontWeightSection(
                    fontWeight = fontWeight,
                    onFontWeightChange = onFontWeightChange,
                )
            }
        }
    }
}

@Composable
internal fun LyricsFontSizeSection(
    fontSizeSp: Int,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(fontSizeSp) {
        mutableFloatStateOf(
            fontSizeSp.coerceIn(
                AppSettings.MIN_LYRICS_FONT_SIZE_SP,
                AppSettings.MAX_LYRICS_FONT_SIZE_SP,
            ).toFloat(),
        )
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lyrics_font_size),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
            )
            Text(
                text = sliderValue.roundToInt().toString(),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        InsetPillSlider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onFontSizeChange(it.roundToInt())
            },
            valueRange = AppSettings.MIN_LYRICS_FONT_SIZE_SP.toFloat()..AppSettings.MAX_LYRICS_FONT_SIZE_SP.toFloat(),
            steps = AppSettings.MAX_LYRICS_FONT_SIZE_SP - AppSettings.MIN_LYRICS_FONT_SIZE_SP - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun LyricsTextCenterSection(
    isTextCentered: Boolean,
    onTextCenteredChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimensions.minimumTouchTarget)
            .clip(MusicTheme.shapes.medium)
            .toggleable(
                value = isTextCentered,
                role = Role.Switch,
                onValueChange = onTextCenteredChange,
            )
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.lyrics_text_center),
            style = MusicTheme.typography.titleMedium,
            color = MusicTheme.colors.onSurface,
        )
        Switch(
            checked = isTextCentered,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun LyricsFontWeightSection(
    fontWeight: Int,
    onFontWeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(fontWeight) {
        mutableFloatStateOf(
            fontWeight.coerceIn(
                AppSettings.MIN_LYRICS_FONT_WEIGHT,
                AppSettings.MAX_LYRICS_FONT_WEIGHT,
            ).toFloat(),
        )
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lyrics_font_weight),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurface,
            )
            Text(
                text = sliderValue.roundToInt().toString(),
                style = MusicTheme.typography.titleMedium,
                color = MusicTheme.colors.onSurfaceVariant,
            )
        }
        val stepsCount = (AppSettings.MAX_LYRICS_FONT_WEIGHT - AppSettings.MIN_LYRICS_FONT_WEIGHT) / AppSettings.LYRICS_FONT_WEIGHT_STEP - 1
        InsetPillSlider(
            value = sliderValue,
            onValueChange = {
                val step = AppSettings.LYRICS_FONT_WEIGHT_STEP
                val stepped = (((it.roundToInt() - AppSettings.MIN_LYRICS_FONT_WEIGHT + step / 2) / step) * step + AppSettings.MIN_LYRICS_FONT_WEIGHT)
                    .coerceIn(AppSettings.MIN_LYRICS_FONT_WEIGHT, AppSettings.MAX_LYRICS_FONT_WEIGHT)
                sliderValue = stepped.toFloat()
                onFontWeightChange(stepped)
            },
            valueRange = AppSettings.MIN_LYRICS_FONT_WEIGHT.toFloat()..AppSettings.MAX_LYRICS_FONT_WEIGHT.toFloat(),
            steps = stepsCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
