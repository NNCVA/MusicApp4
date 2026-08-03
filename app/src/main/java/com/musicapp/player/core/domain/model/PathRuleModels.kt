package com.musicapp.player.core.domain.model

@JvmInline
value class PathRuleId(val value: Long) {
    init {
        require(value > 0) { "PathRuleId must be positive" }
    }
}

enum class PathRuleKind {
    INCLUDE,
    EXCLUDE,
}

data class PathRule(
    val id: PathRuleId,
    val volumeName: String,
    val directory: String,
    val kind: PathRuleKind,
) {
    init {
        require(volumeName.isNotBlank()) { "volumeName must not be blank" }
    }
}
