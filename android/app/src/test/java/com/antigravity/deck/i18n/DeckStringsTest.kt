package com.antigravity.deck.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DeckStringsTest {

    @Test
    fun parseAppLanguageDefaultsToSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.parse("unknown"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.parse("ja"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.parse("EN"))
    }

    @Test
    fun resolveSystemLanguageByLocale() {
        assertEquals(AppLanguage.JAPANESE, AppLanguage.SYSTEM.resolve(Locale.JAPAN))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.SYSTEM.resolve(Locale.US))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.JAPANESE.resolve(Locale.US))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.ENGLISH.resolve(Locale.JAPAN))
    }

    @Test
    fun speechLocaleTagMatchesResolvedLanguage() {
        assertEquals("ja-JP", AppLanguage.JAPANESE.speechLocaleTag(Locale.US))
        assertEquals("en-US", AppLanguage.ENGLISH.speechLocaleTag(Locale.JAPAN))
        assertEquals("ja-JP", AppLanguage.SYSTEM.speechLocaleTag(Locale.JAPAN))
        assertEquals("en-US", AppLanguage.SYSTEM.speechLocaleTag(Locale.US))
    }

    @Test
    fun stringsReturnJapaneseOrEnglish() {
        val ja = DeckI18n.strings(AppLanguage.JAPANESE, Locale.US)
        val en = DeckI18n.strings(AppLanguage.ENGLISH, Locale.JAPAN)

        assertEquals("待機", ja.statusIdle)
        assertEquals("Idle", en.statusIdle)
        assertEquals("閉じる", ja.closeButton)
        assertEquals("Close", en.closeButton)
    }
}
