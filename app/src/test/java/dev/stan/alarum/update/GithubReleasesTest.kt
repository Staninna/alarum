package dev.stan.alarum.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GithubReleasesTest {

    private fun body(assets: String, tag: String = "v1.1.0") = """
        {"tag_name":"$tag","body":"Fixed the siren.\nAlso the maths.","assets":[$assets]}
    """.trimIndent()

    private val apkAsset = """
        {"name":"alarum-v1.1.0.apk","size":3145728,
         "browser_download_url":"https://example.invalid/alarum.apk"}
    """.trimIndent()

    @Test
    fun `picks the apk asset and its size`() {
        val r = GithubReleases.parse(body(apkAsset))
        assertEquals("v1.1.0", r.tag)
        assertEquals("https://example.invalid/alarum.apk", r.apkUrl)
        assertEquals(3_145_728L, r.sizeBytes)
        assertEquals("Fixed the siren.\nAlso the maths.", r.notes)
    }

    @Test
    fun `ignores non-apk assets`() {
        val mapping = """{"name":"mapping.txt","size":10,"browser_download_url":"https://example.invalid/m.txt"}"""
        val r = GithubReleases.parse(body("$mapping,$apkAsset"))
        assertEquals("https://example.invalid/alarum.apk", r.apkUrl)
    }

    @Test
    fun `a release with no apk is an error rather than a silent no-op`() {
        val only = """{"name":"notes.md","size":1,"browser_download_url":"https://example.invalid/n.md"}"""
        val e = assertThrows(ReleaseException::class.java) { GithubReleases.parse(body(only)) }
        assertEquals("Release v1.1.0 has no APK attached.", e.message)
    }

    @Test
    fun `a blank tag is rejected`() {
        assertThrows(ReleaseException::class.java) { GithubReleases.parse(body(apkAsset, tag = "")) }
    }

    @Test
    fun `unreadable json is reported, not thrown raw`() {
        val e = assertThrows(ReleaseException::class.java) { GithubReleases.parse("not json") }
        assertEquals("Couldn't read GitHub's answer.", e.message)
    }
}
