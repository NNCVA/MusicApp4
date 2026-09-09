package com.musicapp.player.feature.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musicapp.player.R
import com.musicapp.player.core.designsystem.component.InsetPillSlider
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.playback.timer.SleepTimerStatus
import com.musicapp.player.theme.MusicTheme
import com.musicapp.player.theme.MusicWindowWidthTier
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SleepTimerSheet(
    sleepTimerStatus: SleepTimerStatus?,
    initialDurationMinutes: Int,
    initialExtendToEndOfTrack: Boolean,
    onStart: (durationMinutes: Int, extendToEndOfTrack: Boolean) -> Unit,
    onStop: () -> Unit,
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
        ) {
            SleepTimerContent(
                sleepTimerStatus = sleepTimerStatus,
                initialDurationMinutes = initialDurationMinutes,
                initialExtendToEndOfTrack = initialExtendToEndOfTrack,
                onStart = { duration, extend ->
                    onStart(duration, extend)
                    onDismiss()
                },
                onStop = onStop,
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
                SleepTimerContent(
                    sleepTimerStatus = sleepTimerStatus,
                    initialDurationMinutes = initialDurationMinutes,
                    initialExtendToEndOfTrack = initialExtendToEndOfTrack,
                    onStart = { duration, extend ->
                        onStart(duration, extend)
                        onDismiss()
                    },
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
internal fun SleepTimerContent(
    sleepTimerStatus: SleepTimerStatus?,
    initialDurationMinutes: Int,
    initialExtendToEndOfTrack: Boolean,
    onStart: (durationMinutes: Int, extendToEndOfTrack: Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimensions = MusicTheme.dimensions
    var durationMinutes by remember(initialDurationMinutes) {
        mutableFloatStateOf(
            initialDurationMinutes.coerceIn(
                AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES,
                AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES,
            ).toFloat(),
        )
    }
    var extendToEndOfTrack by remember(initialExtendToEndOfTrack) {
        mutableStateOf(initialExtendToEndOfTrack)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimensions.spaceLarge, vertical = dimensions.spaceMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spaceMedium),
    ) {
        Text(
            text = stringResource(R.string.playback_sleep_timer),
            style = MusicTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MusicTheme.colors.onSurface,
        )

        if (sleepTimerStatus != null) {
            // Running state: large countdown card + Stop button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MusicTheme.shapes.large,
                color = MusicTheme.aeroCardContainerColor,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = dimensions.spaceLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = sleepTimerStatus.formatRemainingTime(),
                        style = MusicTheme.typography.displayMedium.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MusicTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MusicTheme.colors.primary,
                    contentColor = MusicTheme.colors.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.playback_sleep_timer_stop),
                    style = MusicTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            // Setup state: Duration slider card + Start button + Extend switch card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MusicTheme.shapes.large,
                color = MusicTheme.aeroCardContainerColor,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = dimensions.spaceLarge,
                            vertical = dimensions.spaceMedium,
                        ),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spaceSmall),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.playback_sleep_timer_duration_label),
                            style = MusicTheme.typography.titleMedium,
                            color = MusicTheme.colors.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.playback_sleep_timer_duration_minutes,
                                durationMinutes.roundToInt(),
                            ),
                            style = MusicTheme.typography.titleMedium,
                            color = MusicTheme.colors.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    InsetPillSlider(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        valueRange = AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES.toFloat()..AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES.toFloat(),
                        steps = AppSettings.MAX_SLEEP_TIMER_DURATION_MINUTES - AppSettings.MIN_SLEEP_TIMER_DURATION_MINUTES - 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = {
                    onStart(durationMinutes.roundToInt(), extendToEndOfTrack)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MusicTheme.colors.primary,
                    contentColor = MusicTheme.colors.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.playback_sleep_timer_start),
                    style = MusicTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Auto-extend switch card (following selection-and-toggle-controls specification)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MusicTheme.shapes.large)
                    .toggleable(
                        value = extendToEndOfTrack,
                        role = Role.Switch,
                        onValueChange = { extendToEndOfTrack = it },
                    )
                    .semantics(mergeDescendants = true) {},
                shape = MusicTheme.shapes.large,
                color = MusicTheme.aeroCardContainerColor,
                contentColor = MusicTheme.colors.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensions.minimumTouchTarget)
                        .padding(
                            horizontal = dimensions.spaceLarge,
                            vertical = dimensions.spaceMedium,
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = dimensions.spaceMedium),
                        verticalArrangement = Arrangement.spacedBy(dimensions.spaceExtraSmall),
                    ) {
                        Text(
                            text = stringResource(R.string.playback_sleep_timer_extend_title),
                            style = MusicTheme.typography.titleMedium,
                            color = MusicTheme.colors.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.playback_sleep_timer_extend_desc),
                            style = MusicTheme.typography.bodySmall,
                            color = MusicTheme.colors.onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                    Switch(
                        checked = extendToEndOfTrack,
                        onCheckedChange = null,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }

        // Advice / health care tip card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MusicTheme.shapes.large,
            color = MusicTheme.colors.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Text(
                text = stringResource(R.string.playback_sleep_timer_advice),
                style = MusicTheme.typography.bodySmall,
                color = MusicTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = dimensions.spaceMedium,
                    vertical = dimensions.spaceSmall,
                ),
            )
        }
        Spacer(Modifier.height(dimensions.spaceSmall))
    }
}
