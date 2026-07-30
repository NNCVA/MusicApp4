package com.musicapp.player.core.playback.system

/** Commands that a system playback surface may ask the application to expose. */
enum class SystemPlaybackCommand {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    SEEK,
    STOP,
    EDIT_QUEUE,
    CUSTOM,
}

/** Keeps the notification and system media panel limited to the three primary controls. */
object NotificationCommandPolicy {
    val primaryCommands: List<SystemPlaybackCommand> =
        listOf(
            SystemPlaybackCommand.PREVIOUS,
            SystemPlaybackCommand.PLAY_PAUSE,
            SystemPlaybackCommand.NEXT,
        )

    fun visibleCommands(
        availableCommands: Set<SystemPlaybackCommand>,
    ): List<SystemPlaybackCommand> = primaryCommands.filter(availableCommands::contains)

    fun isVisible(command: SystemPlaybackCommand): Boolean = command in primaryCommands
}
