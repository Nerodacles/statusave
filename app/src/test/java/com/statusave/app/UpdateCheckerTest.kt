package com.statusave.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer patch version is detected`() {
        assertTrue(UpdateChecker.isNewer("1.0.2", "1.0.1"))
    }

    @Test
    fun `same version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.1"))
    }

    @Test
    fun `older version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.0.1", "1.0.2"))
    }

    @Test
    fun `shorter remote version compares by segments`() {
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `newer major version is detected`() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `non numeric segments are treated as zero`() {
        assertFalse(UpdateChecker.isNewer("abc", "1.0.0"))
    }
}
