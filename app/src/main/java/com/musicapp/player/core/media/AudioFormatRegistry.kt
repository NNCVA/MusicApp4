package com.musicapp.player.core.media

import java.util.Locale

/**
 * 音频格式白名单与准入规格配置。
 *
 * 集中维护支持的文件扩展名、MIME 类型别名集合、通用二进制降级嗅探规则以及短音频过滤门限。
 * 详细设计与格式扩展指南参见：docs/design/audio-format-registry.md
 */
object AudioFormatRegistry {
    /**
     * 官方支持的音频文件扩展名白名单（全部小写，不带点号）。
     */
    val SUPPORTED_EXTENSIONS: Set<String> = setOf(
        "mp3",
        "flac",
        "wav",
        "aac",
        "m4a",
        "ogg",
        "opus",
    )

    /**
     * 官方支持的具体音频 MIME 类型白名单。
     *
     * 包含主流标准类型以及各 Android OEM 厂商和打标工具的变体。
     */
    val SUPPORTED_MIME_TYPES: Set<String> = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/x-mp3",
        "audio/x-mpeg",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/vnd.wave",
        "audio/aac",
        "audio/aacp",
        "audio/x-aac",
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/ogg",
        "audio/x-ogg",
        "application/ogg",
        "audio/opus",
        "audio/x-opus",
    )

    /**
     * 触发扩展名降级二次嗅探的通用或模糊二进制 MIME 类型集合。
     *
     * 当 MediaStore 返回此类通用类型（或缺失 MIME）时，转为依据文件名扩展名判断。
     */
    val GENERIC_MIME_TYPES: Set<String> = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/unknown",
        "application/x-unknown",
        "audio/*",
        "unknown/unknown",
    )

    /**
     * 短音频过滤门限时长（毫秒）。默认 60 秒（60,000 ms）。
     *
     * 当用户在扫描设置中开启“过滤短音频”选项时，时长严格小于此门限的曲目将被排除。
     */
    const val MIN_AUDIO_DURATION_MS: Long = 60_000L

    /**
     * 检查给定的文件扩展名是否在受支持白名单中（大小写不敏感）。
     */
    fun isSupportedExtension(extension: String?): Boolean =
        extension?.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS

    /**
     * 检查给定的规范化 MIME 类型是否属于通用/需降级嗅探的类型。
     */
    fun isGenericMimeType(normalizedMimeType: String?): Boolean =
        normalizedMimeType == null || normalizedMimeType in GENERIC_MIME_TYPES

    /**
     * 检查给定的规范化 MIME 类型是否在明确支持的白名单中。
     */
    fun isSupportedMimeType(normalizedMimeType: String?): Boolean =
        normalizedMimeType != null && normalizedMimeType in SUPPORTED_MIME_TYPES
}
