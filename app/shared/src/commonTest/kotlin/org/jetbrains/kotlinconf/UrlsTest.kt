package org.jetbrains.kotlinconf

import kotlin.test.Test
import kotlin.test.assertTrue

class UrlsTest {
    @Test
    fun reportProblemLinkNamesTheAppVersion() {
        val url = URLs.reportProblem("40.0.5 (79)")
        assertTrue(url.startsWith(URLs.GITHUB_REPO + "/issues/new?body="), url)
        assertTrue("40.0.5" in url && " " !in url && "\n" !in url, "the version is in the link, and the link is encoded: $url")
    }
}
