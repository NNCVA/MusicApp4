package com.musicapp.player.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicapp.player.core.domain.model.AppSettings
import com.musicapp.player.core.domain.model.Track
import com.musicapp.player.core.domain.model.TrackId
import com.musicapp.player.core.lyrics.LyricsAutoCenterController
import com.musicapp.player.core.lyrics.LyricsRepository
import com.musicapp.player.core.lyrics.LyricsSource
import com.musicapp.player.core.lyrics.LyricsSynchronizer
import com.musicapp.player.core.lyrics.MissingLyrics
import com.musicapp.player.core.lyrics.ResolvedLyrics
import com.musicapp.player.core.lyrics.StaticLyrics
import com.musicapp.player.core.lyrics.SynchronizedLyrics
import com.musicapp.player.core.lyrics.TimedLyricLine
import com.musicapp.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LyricsDisplayMode {
    LOADING,
    SYNCHRONIZED,
    STATIC,
    MISSING,
}

data class LyricsUiState(
    val mode: LyricsDisplayMode = LyricsDisplayMode.MISSING,
    val source: LyricsSource? = null,
    val lines: List<TimedLyricLine> = emptyList(),
    val activeLineIndex: Int? = null,
    val previousLine: String = "",
    val currentLine: String = "",
    val nextLine: String = "",
    val staticText: String? = null,
    val missingStringResourceKey: String? = MissingLyrics.stringResourceKey,
    val autoCenterEnabled: Boolean = true,
    val autoCenterRequest: Long = 0,
    val fontSizeSp: Int = AppSettings.DEFAULT_LYRICS_FONT_SIZE_SP,
    val isTextCentered: Boolean = false,
    val fontWeight: Int = AppSettings.DEFAULT_LYRICS_FONT_WEIGHT,
    val isSettingsSheetVisible: Boolean = false,
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {
    private val synchronizer = LyricsSynchronizer()
    private val mutableUiState = MutableStateFlow(LyricsUiState())
    private val mutableSeekRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private var resolvedLyrics: ResolvedLyrics = MissingLyrics
    private var playbackPositionMs = 0L
    private var loadedTrackKey: Pair<TrackId, Long>? = null
    private var loadJob: Job? = null
    private var autoCenterJob: Job? = null

    val uiState: StateFlow<LyricsUiState> = mutableUiState.asStateFlow()
    val seekRequests: SharedFlow<Long> = mutableSeekRequests.asSharedFlow()

    init {
        settingsRepository?.let { settingsRepo ->
            viewModelScope.launch {
                settingsRepo.settings.collect { settings ->
                    mutableUiState.value = mutableUiState.value.copy(
                        fontSizeSp = settings.lyricsFontSizeSp,
                        isTextCentered = settings.lyricsTextCentered,
                        fontWeight = settings.lyricsFontWeight,
                    )
                }
            }
        }
    }

    fun load(track: Track?) {
        val key = track?.let { it.id to it.dateModifiedMs }
        if (key == loadedTrackKey) return
        loadedTrackKey = key
        loadJob?.cancel()
        autoCenterJob?.cancel()
        playbackPositionMs = 0
        resolvedLyrics = MissingLyrics
        val current = mutableUiState.value
        mutableUiState.value = if (track == null) {
            current.copy(
                mode = LyricsDisplayMode.MISSING,
                source = null,
                lines = emptyList(),
                activeLineIndex = null,
                previousLine = "",
                currentLine = "",
                nextLine = "",
                staticText = null,
                missingStringResourceKey = MissingLyrics.stringResourceKey,
            )
        } else {
            current.copy(
                mode = LyricsDisplayMode.LOADING,
                source = null,
                lines = emptyList(),
                activeLineIndex = null,
                previousLine = "",
                currentLine = "",
                nextLine = "",
                staticText = null,
                missingStringResourceKey = null,
            )
        }
        if (track == null) return
        loadJob = viewModelScope.launch {
            val loaded = repository.load(track)
            if (loadedTrackKey == key) {
                resolvedLyrics = loaded
                mutableUiState.value = loaded.toUiState(playbackPositionMs)
            }
        }
    }

    fun updatePlaybackPosition(positionMs: Long) {
        playbackPositionMs = positionMs.coerceAtLeast(0)
        if (resolvedLyrics !is SynchronizedLyrics) return
        val sync = synchronizer.synchronize(resolvedLyrics, playbackPositionMs)
        val lineChanged = sync.activeLineIndex != mutableUiState.value.activeLineIndex
        if (lineChanged && sync.activeLineIndex != null) {
            autoCenterJob?.cancel()
            mutableUiState.value = mutableUiState.value.copy(
                activeLineIndex = sync.activeLineIndex,
                previousLine = sync.previousLine,
                currentLine = sync.currentLine,
                nextLine = sync.nextLine,
                autoCenterEnabled = true,
                autoCenterRequest = mutableUiState.value.autoCenterRequest + 1,
            )
        } else {
            mutableUiState.value = mutableUiState.value.copy(
                activeLineIndex = sync.activeLineIndex,
                previousLine = sync.previousLine,
                currentLine = sync.currentLine,
                nextLine = sync.nextLine,
            )
        }
    }

    fun onManualScroll() {
        if (resolvedLyrics !is SynchronizedLyrics) return
        autoCenterJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(autoCenterEnabled = false)
        autoCenterJob = viewModelScope.launch {
            delay(LyricsAutoCenterController.DEFAULT_RESUME_DELAY_MS)
            resumeAutoCenter()
        }
    }

    fun returnToCurrentLine() {
        autoCenterJob?.cancel()
        resumeAutoCenter()
    }

    fun onLineClick(lineIndex: Int) {
        autoCenterJob?.cancel()
        resumeAutoCenter()
        synchronizer.seekPositionMs(resolvedLyrics, lineIndex)?.let(mutableSeekRequests::tryEmit)
    }

    fun setFontSizeSp(size: Int) {
        val clamped = size.coerceIn(AppSettings.MIN_LYRICS_FONT_SIZE_SP, AppSettings.MAX_LYRICS_FONT_SIZE_SP)
        mutableUiState.value = mutableUiState.value.copy(fontSizeSp = clamped)
        viewModelScope.launch {
            settingsRepository?.setLyricsFontSizeSp(clamped)
        }
    }

    fun setTextCentered(centered: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(isTextCentered = centered)
        viewModelScope.launch {
            settingsRepository?.setLyricsTextCentered(centered)
        }
    }

    fun setFontWeight(weight: Int) {
        val clamped = weight.coerceIn(AppSettings.MIN_LYRICS_FONT_WEIGHT, AppSettings.MAX_LYRICS_FONT_WEIGHT)
        val stepped = (clamped / AppSettings.LYRICS_FONT_WEIGHT_STEP) * AppSettings.LYRICS_FONT_WEIGHT_STEP
        mutableUiState.value = mutableUiState.value.copy(fontWeight = stepped)
        viewModelScope.launch {
            settingsRepository?.setLyricsFontWeight(stepped)
        }
    }

    fun showSettings() {
        mutableUiState.value = mutableUiState.value.copy(isSettingsSheetVisible = true)
    }

    fun dismissSettings() {
        mutableUiState.value = mutableUiState.value.copy(isSettingsSheetVisible = false)
    }

    private fun resumeAutoCenter() {
        mutableUiState.value = mutableUiState.value.copy(
            autoCenterEnabled = true,
            autoCenterRequest = mutableUiState.value.autoCenterRequest + 1,
        )
    }

    private fun ResolvedLyrics.toUiState(positionMs: Long): LyricsUiState {
        val current = mutableUiState.value
        return when (this) {
            is SynchronizedLyrics -> {
                val sync = synchronizer.synchronize(this, positionMs)
                current.copy(
                    mode = LyricsDisplayMode.SYNCHRONIZED,
                    source = source,
                    lines = lines,
                    activeLineIndex = sync.activeLineIndex,
                    previousLine = sync.previousLine,
                    currentLine = sync.currentLine,
                    nextLine = sync.nextLine,
                    staticText = null,
                    missingStringResourceKey = null,
                )
            }

            is StaticLyrics -> current.copy(
                mode = LyricsDisplayMode.STATIC,
                source = source,
                lines = emptyList(),
                activeLineIndex = null,
                previousLine = "",
                currentLine = "",
                nextLine = "",
                staticText = text,
                missingStringResourceKey = null,
            )

            MissingLyrics -> current.copy(
                mode = LyricsDisplayMode.MISSING,
                source = null,
                lines = emptyList(),
                activeLineIndex = null,
                previousLine = "",
                currentLine = "",
                nextLine = "",
                staticText = null,
                missingStringResourceKey = MissingLyrics.stringResourceKey,
            )
        }
    }
}
