package com.musicapp.player.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
)

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
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

    fun load(track: Track?) {
        val key = track?.let { it.id to it.dateModifiedMs }
        if (key == loadedTrackKey) return
        loadedTrackKey = key
        loadJob?.cancel()
        autoCenterJob?.cancel()
        playbackPositionMs = 0
        resolvedLyrics = MissingLyrics
        mutableUiState.value = if (track == null) LyricsUiState() else LyricsUiState(
            mode = LyricsDisplayMode.LOADING,
            missingStringResourceKey = null,
        )
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
        mutableUiState.value = mutableUiState.value.copy(
            activeLineIndex = sync.activeLineIndex,
            previousLine = sync.previousLine,
            currentLine = sync.currentLine,
            nextLine = sync.nextLine,
        )
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
        synchronizer.seekPositionMs(resolvedLyrics, lineIndex)?.let(mutableSeekRequests::tryEmit)
    }

    private fun resumeAutoCenter() {
        mutableUiState.value = mutableUiState.value.copy(
            autoCenterEnabled = true,
            autoCenterRequest = mutableUiState.value.autoCenterRequest + 1,
        )
    }

    private fun ResolvedLyrics.toUiState(positionMs: Long): LyricsUiState =
        when (this) {
            is SynchronizedLyrics -> {
                val sync = synchronizer.synchronize(this, positionMs)
                LyricsUiState(
                    mode = LyricsDisplayMode.SYNCHRONIZED,
                    source = source,
                    lines = lines,
                    activeLineIndex = sync.activeLineIndex,
                    previousLine = sync.previousLine,
                    currentLine = sync.currentLine,
                    nextLine = sync.nextLine,
                    missingStringResourceKey = null,
                )
            }

            is StaticLyrics -> LyricsUiState(
                mode = LyricsDisplayMode.STATIC,
                source = source,
                staticText = text,
                missingStringResourceKey = null,
            )

            MissingLyrics -> LyricsUiState()
        }
}
