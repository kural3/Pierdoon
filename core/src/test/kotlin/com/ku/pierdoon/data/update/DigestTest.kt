package com.ku.pierdoon.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DigestTest {

    @Test
    fun readsGitHubsSha256() {
        val hex = "c6af40e3965f05d6f7e5c75e3f5c95ec5497a0496c9d08ca77f07408a2138ff2"
        assertEquals(hex, sha256Of("sha256:$hex"))
        assertEquals(hex, sha256Of("sha256:${hex.uppercase()}"))
    }

    @Test
    fun anythingElseIsNoDigest() {
        assertNull(sha256Of(""))
        assertNull(sha256Of("sha512:abcd"))
        assertNull(sha256Of("sha256:abcd"))
        assertNull(sha256Of("sha256:" + "z".repeat(64)))
    }
}
