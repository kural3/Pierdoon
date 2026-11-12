package com.ku.pierdoon

import com.ku.pierdoon.data.update.AppRelease
import com.ku.pierdoon.data.update.AppUpdater
import com.ku.pierdoon.data.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the "new version" dialog of both apps comes up, and what its buttons do. */
class UpdatePromptTest {

    private val release = AppRelease("9.9", "notes", "https://example.invalid/app", 1)

    private class FakeUpdater(var due: Boolean, var found: UpdateState) : AppUpdater {
        override val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val skipped = mutableSetOf<String>()
        var checks = 0
        var cancels = 0
        var installed: AppRelease? = null
        override fun isDue() = due
        override fun isSkipped(release: AppRelease) = release.version in skipped
        override fun skip(release: AppRelease) { skipped += release.version }
        override suspend fun check(): UpdateState { checks++; state.value = found; return found }
        override fun install(release: AppRelease) { installed = release }
        override fun cancel() { cancels++ }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun stop() = scope.cancel()

    private fun StateFlow<Boolean>.await(value: Boolean) = runBlocking { withTimeout(5_000) { first { it == value } } }

    /** Gives a launched check time to finish, for the cases where nothing should happen. */
    private fun settle() = Thread.sleep(200)

    @Test
    fun aNewReleaseFoundOnLaunchIsShown() {
        val updater = FakeUpdater(due = true, found = UpdateState.Available(release))
        val prompt = UpdatePrompt(scope, updater, autoCheck = true)
        prompt.shown.await(true)
        assertEquals(1, updater.checks)
    }

    @Test
    fun noCheckOnLaunchWhenSwitchedOffOrNotDue() {
        val off = FakeUpdater(due = true, found = UpdateState.Available(release))
        UpdatePrompt(scope, off, autoCheck = false)
        val notDue = FakeUpdater(due = false, found = UpdateState.Available(release))
        UpdatePrompt(scope, notDue, autoCheck = true)
        settle()
        assertEquals(0, off.checks)
        assertEquals(0, notDue.checks)
    }

    @Test
    fun laterKeepsTheLaunchCheckQuietButNotAManualOne() {
        val updater = FakeUpdater(due = true, found = UpdateState.Available(release))
        updater.skipped += release.version
        val prompt = UpdatePrompt(scope, updater, autoCheck = true)
        settle()
        assertEquals(1, updater.checks)
        assertFalse(prompt.shown.value)

        prompt.check()
        prompt.shown.await(true)
    }

    @Test
    fun aManualCheckWithNothingNewShowsNoDialog() {
        val updater = FakeUpdater(due = false, found = UpdateState.UpToDate)
        val prompt = UpdatePrompt(scope, updater, autoCheck = true)
        prompt.check()
        settle()
        assertEquals(1, updater.checks)
        assertFalse(prompt.shown.value)
    }

    @Test
    fun laterOnAReleasePutsItOff() {
        val updater = FakeUpdater(due = true, found = UpdateState.Available(release))
        val prompt = UpdatePrompt(scope, updater, autoCheck = true)
        prompt.shown.await(true)
        prompt.dismiss()
        assertFalse(prompt.shown.value)
        assertTrue(updater.isSkipped(release))
        assertEquals(0, updater.cancels)
    }

    @Test
    fun closingDuringADownloadStopsItAndSkipsNothing() {
        val updater = FakeUpdater(due = false, found = UpdateState.UpToDate)
        val prompt = UpdatePrompt(scope, updater, autoCheck = true)
        prompt.show()
        prompt.install(release)
        assertEquals(release, updater.installed)
        updater.state.value = UpdateState.Downloading(release, 0.5f)
        prompt.dismiss()
        assertEquals(1, updater.cancels)
        assertFalse(updater.isSkipped(release))
        assertFalse(prompt.shown.value)
    }
}
