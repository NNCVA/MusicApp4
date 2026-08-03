package com.musicapp.player.core.lyrics

import com.musicapp.player.core.domain.model.Track

fun interface LyricsRepository {
    suspend fun load(track: Track): ResolvedLyrics
}
