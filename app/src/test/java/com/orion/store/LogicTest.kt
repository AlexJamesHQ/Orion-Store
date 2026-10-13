package com.orion.store

import com.orion.store.data.*
import com.orion.store.ui.bundleIssueBody
import com.orion.store.ui.cleanPermName
import com.orion.store.ui.isProfane
import com.orion.store.ui.webSettingsImport

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun versions() {
        assertEquals("1.2.3", normalizeVersion("v1.2.3-beta+5"))
        assertTrue(compareVersions("1.10", "1.9") > 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertEquals(0, compareVersions(null, "1.0"))
        assertTrue(isSameVersion("v2.0", "2.0-rc"))
        assertTrue(isStoreUpdateAvailable("1.5.5", "1.6.0"))
        assertFalse(shouldForceStoreUpdate("1.5.5", "1.5.5"))
    }

    @Test fun imageLinks() {
        assertNull(explainRejectedImageLink("https://example.com/a.png"))
        assertNull(explainRejectedImageLink("https://raw.githubusercontent.com/o/r/main/x"))
        assertEquals("Link must start with http:// or https://", explainRejectedImageLink("ftp://x/a.png"))
        assertNotNull(explainRejectedImageLink("https://example.com/page"))
        assertEquals("https://raw.githubusercontent.com/o/r/main/s.png", sanitizeGitHubImageUrl("https://github.com/o/r/blob/main/s.png"))
        assertEquals("https://x.com/a.png", sanitizeGitHubImageUrl("https://x.com/a.png"))
        assertEquals("https://play-lh.googleusercontent.com/abc=h720-rw", optimizedImageUrl("https://play-lh.googleusercontent.com/abc=w526-h296", null, 720))
        assertEquals("https://play-lh.googleusercontent.com/abc=w256-h256-rw", optimizedImageUrl("https://play-lh.googleusercontent.com/abc", 256, 256))
        assertTrue(optimizedImageUrl("https://raw.githubusercontent.com/o/r/main/s.png", null, 720).startsWith("https://wsrv.nl/?url="))
    }

    @Test fun repoParsing() {
        assertEquals(RepoSource("github", "o/app", "o", "app"), parseRepoSource("https://github.com/o/app.git"))
        assertEquals(RepoSource("github", "o/app", "o", "app"), parseRepoSource("https://github.com/o/app/tree/main/src"))
        assertEquals(RepoSource("gitlab", "g/sub/app", "g", "app", "gitlab.com"), parseRepoSource("https://gitlab.com/g/sub/app/-/releases"))
        assertEquals("codeberg", parseRepoSource("https://codeberg.org/o/r")?.provider)
        assertNull(parseRepoSource("https://example.com/o/r"))
        assertNull(parseRepoSource("https://github.com/o"))
        assertEquals("com.foo.bar", extractPackageName("android {\n  namespace = \"x.y\"\n  defaultConfig { applicationId \"com.foo.bar\" }\n}"))
        assertEquals("org.x", extractPackageName("<manifest package=\"org.x\">"))
        assertEquals("My Cool App", humanizeRepoName("my-cool_app.git"))
        assertEquals("my-app-2", slugId("  My App 2!! "))
    }

    @Test fun submissionUrls() {
        val u = submissionIssueUrl("[]", 2, "Foo", "1.5.5")
        assertTrue(u.startsWith("https://github.com/RookieEnough/Orion-Data/issues/new?title="))
        assertTrue(enc("App Submission [2 Apps] - Foo") in u)
        val body = bundleIssueBody("T", "D", "fa-bolt", "c", listOf("a", "zz"), listOf(AppItem(id = "a", name = "A", packageName = "p.a")), null)
        assertTrue("- **A** (`a`) · `p.a`" in body)
        assertTrue("- zz" in body)
        assertFalse("Submitted by" in body)
        assertFalse("\n\n" in body)
    }

    @Test fun categories() {
        val music = AppItem(id = "m", name = "Tunes", description = "Listen to your album collection", category = "Media")
        val tool = AppItem(id = "t", name = "Box", category = "Utility", tags = listOf("Privacy"))
        assertTrue(appMatchesCategoryFilter(music, "Music")) // preset keyword in description
        assertTrue(appMatchesCategoryFilter(tool, "privacy")) // tag, case-insensitive
        assertTrue(appMatchesCategoryFilter(tool, "Utilities")) // preset raw category
        assertFalse(appMatchesCategoryFilter(tool, "Music"))
        val cards = homepageCategoryCards(listOf(music, tool, tool.copy(id = "t2")))
        assertEquals(1, cards.first { it.label == "Music" }.count)
        assertEquals(2, cards.first { it.label == "Utilities" }.count)
        assertEquals(listOf("All", "Music", "Privacy", "Utilities", "Media", "Utility"), availableFilterCategories(listOf(music, tool)))
    }

    @Test fun catalogCacheRoundTrip() {
        val c = CatalogCache(listOf(AppItem(id = "a", name = "A", variants = listOf(AppVariant("ARM64", "https://x/a.apk", 42)))), null, "GitHub")
        assertEquals(c, json.decodeFromString(CatalogCache.serializer(), json.encodeToString(CatalogCache.serializer(), c)))
    }

    @Test fun webCarryOver() {
        val s = webSettingsImport("""{"state":{"adWatchCount":42,"unlockedBadges":["Dino Rookie"],"theme":"dark","isLegend":true},"version":0}""", Settings())
        assertEquals(42, s.adWatchCount); assertEquals(listOf("Dino Rookie"), s.unlockedBadges); assertEquals("dark", s.theme); assertTrue(s.isLegend)
        // A clashing field type drops to the identity fields, which still carry the profile counters
        val f = webSettingsImport("""{"state":{"adWatchCount":7,"unlockedBadges":{"bad":1}}}""", Settings())
        assertEquals(7, f.adWatchCount)
        assertEquals(Settings(), webSettingsImport("not json", Settings()))
    }

    @Test fun misc() {
        assertEquals(1, Xp.level(0)); assertEquals(2, Xp.level(60)); assertEquals(10, Xp.level(900)); assertEquals(9, Xp.level(899))
        assertEquals(30, Xp.level(5800)); assertEquals(50, Xp.level(14700)); assertEquals(Xp.start(50), 14700L)
        assertTrue(isProfane("b4d guy", listOf("bad")))
        assertFalse(isProfane("good", listOf("bad")))
        assertEquals("FINE LOCATION", cleanPermName("android.permission.ACCESS_FINE_LOCATION"))
    }
}
