package com.musicapp.player.data.repository.api

sealed interface RepositoryResult<out T> {
  data class Success<T>(val value: T) : RepositoryResult<T>

  data class Failure(val error: RepositoryError) : RepositoryResult<Nothing>
}

sealed interface RepositoryError {
  data class NotFound(val resource: Resource, val stableId: String) : RepositoryError

  data class AlreadyExists(val resource: Resource, val comparisonKey: String) : RepositoryError

  data class InvalidInput(val reason: InvalidInputReason) : RepositoryError

  data object PersistenceUnavailable : RepositoryError
}

enum class Resource {
  TRACK,
  PLAYLIST,
  PATH_RULE,
  PLAYBACK_SNAPSHOT,
}

enum class InvalidInputReason {
  BLANK_PLAYLIST_NAME,
  PLAYLIST_NAME_TOO_LONG,
  INVALID_PLAYLIST_NAME,
  INVALID_PLAYBACK_SNAPSHOT,
  INVALID_TIME,
  INVALID_SYNC_GENERATION,
}
