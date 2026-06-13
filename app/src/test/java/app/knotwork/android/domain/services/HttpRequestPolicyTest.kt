package app.knotwork.android.domain.services

import app.knotwork.android.domain.models.ToolRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HttpRequestPolicy] — the pure security policy behind the
 * `http_request` tool. Each rule is exercised on both its accept and reject
 * paths so the executor's refusals are anchored to verified behaviour.
 */
class HttpRequestPolicyTest {

    @Test
    fun `given GET when methodRisk then SENSITIVE`() {
        assertEquals(ToolRisk.SENSITIVE, HttpRequestPolicy.methodRisk("GET"))
    }

    @Test
    fun `given lowercase get when methodRisk then SENSITIVE`() {
        assertEquals(ToolRisk.SENSITIVE, HttpRequestPolicy.methodRisk(" get "))
    }

    @Test
    fun `given POST PUT DELETE when methodRisk then DESTRUCTIVE`() {
        assertEquals(ToolRisk.DESTRUCTIVE, HttpRequestPolicy.methodRisk("POST"))
        assertEquals(ToolRisk.DESTRUCTIVE, HttpRequestPolicy.methodRisk("PUT"))
        assertEquals(ToolRisk.DESTRUCTIVE, HttpRequestPolicy.methodRisk("DELETE"))
    }

    @Test
    fun `given unsupported method when methodRisk then null`() {
        assertNull(HttpRequestPolicy.methodRisk("PATCH"))
        assertNull(HttpRequestPolicy.methodRisk("HEAD"))
        assertNull(HttpRequestPolicy.methodRisk(""))
    }

    @Test
    fun `given body methods when methodAllowsBody then true only for write verbs`() {
        assertTrue(HttpRequestPolicy.methodAllowsBody("POST"))
        assertTrue(HttpRequestPolicy.methodAllowsBody("put"))
        assertTrue(HttpRequestPolicy.methodAllowsBody("DELETE"))
        assertFalse(HttpRequestPolicy.methodAllowsBody("GET"))
    }

    @Test
    fun `given scheme path and case when normalizeDomain then bare lowercase host`() {
        assertEquals("api.example.com", HttpRequestPolicy.normalizeDomain("https://API.Example.com/v1/items"))
        assertEquals("example.com", HttpRequestPolicy.normalizeDomain("  example.com  "))
        assertEquals("localhost", HttpRequestPolicy.normalizeDomain("http://localhost:11434"))
        assertEquals("a.example.com", HttpRequestPolicy.normalizeDomain("a.example.com?x=1"))
    }

    @Test
    fun `given invalid input when normalizeDomain then null`() {
        assertNull(HttpRequestPolicy.normalizeDomain(""))
        assertNull(HttpRequestPolicy.normalizeDomain("https://"))
        assertNull(HttpRequestPolicy.normalizeDomain("has space.com"))
        assertNull(HttpRequestPolicy.normalizeDomain("bad_underscore.com"))
        assertNull(HttpRequestPolicy.normalizeDomain("-leadinghyphen.com"))
    }

    @Test
    fun `given exact and subdomain hosts when isHostAllowed then true`() {
        val allowed = listOf("example.com", "test.org")
        assertTrue(HttpRequestPolicy.isHostAllowed("example.com", allowed))
        assertTrue(HttpRequestPolicy.isHostAllowed("api.example.com", allowed))
        assertTrue(HttpRequestPolicy.isHostAllowed("deep.api.example.com", allowed))
    }

    @Test
    fun `given non-matching or suffix-trick host when isHostAllowed then false`() {
        val allowed = listOf("example.com")
        assertFalse(HttpRequestPolicy.isHostAllowed("other.com", allowed))
        // notexample.com must NOT match example.com (suffix without dot boundary)
        assertFalse(HttpRequestPolicy.isHostAllowed("notexample.com", allowed))
        assertFalse(HttpRequestPolicy.isHostAllowed("evil-example.com", allowed))
    }

    @Test
    fun `given empty allowlist when isHostAllowed then false`() {
        assertFalse(HttpRequestPolicy.isHostAllowed("example.com", emptyList()))
        assertFalse(HttpRequestPolicy.isHostAllowed("", listOf("example.com")))
    }

    @Test
    fun `given loopback and private hosts when isLoopbackOrPrivateHost then true`() {
        assertTrue(HttpRequestPolicy.isLoopbackOrPrivateHost("localhost"))
        assertTrue(HttpRequestPolicy.isLoopbackOrPrivateHost("127.0.0.1"))
        assertTrue(HttpRequestPolicy.isLoopbackOrPrivateHost("10.0.2.2"))
        assertTrue(HttpRequestPolicy.isLoopbackOrPrivateHost("192.168.1.100"))
        assertTrue(HttpRequestPolicy.isLoopbackOrPrivateHost("172.17.0.1"))
    }

    @Test
    fun `given public host or out-of-range octets when isLoopbackOrPrivateHost then false`() {
        assertFalse(HttpRequestPolicy.isLoopbackOrPrivateHost("example.com"))
        assertFalse(HttpRequestPolicy.isLoopbackOrPrivateHost("8.8.8.8"))
        assertFalse(HttpRequestPolicy.isLoopbackOrPrivateHost("172.32.0.1")) // just outside 16..31
        assertFalse(HttpRequestPolicy.isLoopbackOrPrivateHost("999.1.1.1"))
        assertFalse(HttpRequestPolicy.isLoopbackOrPrivateHost("10.0.0"))
    }

    @Test
    fun `given a secret present in a header or body when leaksCredential then true`() {
        val secrets = listOf("sk-secret-123", "")
        assertTrue(HttpRequestPolicy.leaksCredential(listOf("Bearer sk-secret-123"), secrets))
        assertTrue(HttpRequestPolicy.leaksCredential(listOf("{\"token\":\"sk-secret-123\"}"), secrets))
    }

    @Test
    fun `given no secret match or no secrets when leaksCredential then false`() {
        assertFalse(HttpRequestPolicy.leaksCredential(listOf("Bearer public"), listOf("sk-secret-123")))
        assertFalse(HttpRequestPolicy.leaksCredential(listOf("anything"), emptyList()))
        assertFalse(HttpRequestPolicy.leaksCredential(listOf("anything"), listOf("", "   ")))
    }
}
