package com.antigravity.deck.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun compareVersions_handlesSemverAndPrefix() {
        assertTrue(AppUpdateManager.compareVersions("0.1.0", "v0.2.0") < 0)
        assertEquals(0, AppUpdateManager.compareVersions("0.2.0", "v0.2.0"))
        assertTrue(AppUpdateManager.compareVersions("0.2.1", "0.2.0") > 0)
        assertTrue(AppUpdateManager.compareVersions("0.1.9", "0.10.0") < 0)
    }

    @Test
    fun parseLatestApkRelease_extractsApkAsset() {
        val raw = """
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/Altairu/Antigravity-Deck/releases/tag/v0.2.0",
              "body": "自動更新に対応しました",
              "assets": [
                {
                  "name": "antigravity-deck-0.2.0.vsix",
                  "browser_download_url": "https://example.com/ext.vsix"
                },
                {
                  "name": "antigravity-deck-0.2.0.apk",
                  "browser_download_url": "https://example.com/app.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseLatestApkRelease(raw)
        assertNotNull(release)
        assertEquals("v0.2.0", release!!.tagName)
        assertEquals("0.2.0", release.version)
        assertEquals("antigravity-deck-0.2.0.apk", release.assetName)
        assertEquals("https://example.com/app.apk", release.downloadUrl)
    }

    @Test
    fun parseLatestApkRelease_ignoresDraftsAndMissingApk() {
        val draftJson = """{"tag_name":"v0.3.0","draft":true,"assets":[{"name":"app.apk","browser_download_url":"https://example.com/app.apk"}]}"""
        assertNull(AppUpdateManager.parseLatestApkRelease(draftJson))

        val vsixOnlyJson = """{"tag_name":"v0.3.0","assets":[{"name":"ext.vsix","browser_download_url":"https://example.com/ext.vsix"}]}"""
        assertNull(AppUpdateManager.parseLatestApkRelease(vsixOnlyJson))
    }
}
