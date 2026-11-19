package com.antigravity.deck.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private val SHORT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)

private val FALLBACK_TIME_REGEX = Regex("""T(\d{2}:\d{2})""")
private val FALLBACK_DATE_TIME_REGEX = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})""")

/**
 * ISO 8601 形式のタイムスタンプ文字列を指定されたタイムゾーンの LocalDateTime へ変換する。
 */
private fun parseToLocalDateTime(timestamp: String, zoneId: ZoneId): LocalDateTime? {
    return runCatching {
        OffsetDateTime.parse(timestamp).atZoneSameInstant(zoneId).toLocalDateTime()
    }.getOrNull()
        ?: runCatching {
            ZonedDateTime.parse(timestamp).withZoneSameInstant(zoneId).toLocalDateTime()
        }.getOrNull()
        ?: runCatching {
            Instant.parse(timestamp).atZone(zoneId).toLocalDateTime()
        }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(timestamp)
        }.getOrNull()
}

/**
 * タイムスタンプ文字列を正規化し、パース成功時はフォーマッタで、失敗時はフォールバックで整形する共通処理。
 */
private inline fun formatTimestamp(
    timestamp: String?,
    zoneId: ZoneId,
    formatter: DateTimeFormatter,
    fallback: (String) -> String?
): String? {
    val trimmed = timestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val localDateTime = parseToLocalDateTime(trimmed, zoneId)
    if (localDateTime != null) {
        return localDateTime.format(formatter)
    }
    return fallback(trimmed)
}

/**
 * タイムスタンプを指定タイムゾーンの時刻文字列（HH:mm）へ整形する。
 */
internal fun formatShortTime(
    timestamp: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? {
    return formatTimestamp(timestamp, zoneId, SHORT_TIME_FORMATTER) { raw ->
        FALLBACK_TIME_REGEX.find(raw)?.groupValues?.getOrNull(1)
    }
}

/**
 * タイムスタンプを指定タイムゾーンの日時文字列（yyyy-MM-dd HH:mm）へ整形する。
 */
internal fun formatShortDateTime(
    timestamp: String?,
    zoneId: ZoneId = ZoneId.systemDefault()
): String? {
    return formatTimestamp(timestamp, zoneId, SHORT_DATE_TIME_FORMATTER) { raw ->
        val match = FALLBACK_DATE_TIME_REGEX.find(raw) ?: return@formatTimestamp null
        "${match.groupValues[1]} ${match.groupValues[2]}"
    }
}
