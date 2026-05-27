package com.alex.hubplay.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Spec around [TrailerHost] — the singleton that manages trailer
 * claims across Home / Detail / Series screens. Pure JVM tests; the
 * CoroutineScope is a [TestScope] from kotlinx-coroutines-test so
 * we can advance virtual time to verify the 500ms hide debounce.
 *
 * Coverage:
 *  - activate / deactivate basics
 *  - claim priority (latest wins)
 *  - continuity: same videoKey preserves revealed + time
 *  - different videoKey resets state
 *  - hide debounce (500ms) and its cancellation
 *  - hideNow bypasses debounce
 *  - reportPlaying / reportEnded / reportTime
 *  - embeddability cache
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrailerHostTest {

    @Test
    fun `activate sets current to the requested trailer`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key1", "YouTube")

        assertThat(host.current.value).isNotNull()
        assertThat(host.current.value?.itemId).isEqualTo("item1")
        assertThat(host.current.value?.videoKey).isEqualTo("key1")
        assertThat(host.current.value?.site).isEqualTo("YouTube")
    }

    @Test
    fun `second activate overrides current with latest claim`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key1", "YouTube")
        host.activate("item2", "key2", "YouTube")

        assertThat(host.current.value?.itemId).isEqualTo("item2")
        assertThat(host.current.value?.videoKey).isEqualTo("key2")
    }

    @Test
    fun `deactivate last claim clears current after debounce`() = runTest {
        val host = TrailerHost(this)
        val token = host.activate("item1", "key1", "YouTube")
        host.deactivate(token)

        assertThat(host.current.value).isNotNull()

        advanceTimeBy(600)

        assertThat(host.current.value).isNull()
        assertThat(host.revealed.value).isFalse()
        assertThat(host.currentTimeSec.value).isEqualTo(0L)
    }

    @Test
    fun `deactivate with remaining claims keeps current`() = runTest {
        val host = TrailerHost(this)
        val token1 = host.activate("item1", "key1", "YouTube")
        host.activate("item2", "key2", "YouTube")
        host.deactivate(token1)

        assertThat(host.current.value?.itemId).isEqualTo("item2")

        advanceTimeBy(600)

        assertThat(host.current.value?.itemId).isEqualTo("item2")
    }

    @Test
    fun `same videoKey preserves revealed state on new claim`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key-shared", "YouTube")
        host.reportPlaying()
        host.reportTime(42)

        assertThat(host.revealed.value).isTrue()
        assertThat(host.currentTimeSec.value).isEqualTo(42L)

        host.activate("item2", "key-shared", "YouTube")

        assertThat(host.revealed.value).isTrue()
        assertThat(host.currentTimeSec.value).isEqualTo(42L)
        assertThat(host.current.value?.itemId).isEqualTo("item2")
    }

    @Test
    fun `different videoKey resets revealed and time`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key1", "YouTube")
        host.reportPlaying()
        host.reportTime(42)

        host.activate("item2", "key2", "YouTube")

        assertThat(host.revealed.value).isFalse()
        assertThat(host.currentTimeSec.value).isEqualTo(0L)
    }

    @Test
    fun `different videoKey with startAtSec seeds the time`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key1", "YouTube")
        host.reportPlaying()

        host.activate("item2", "key2", "YouTube", startAtSec = 120L)

        assertThat(host.revealed.value).isFalse()
        assertThat(host.currentTimeSec.value).isEqualTo(120L)
    }

    @Test
    fun `hideNow clears everything immediately without debounce`() = runTest {
        val host = TrailerHost(this)
        host.activate("item1", "key1", "YouTube")
        host.reportPlaying()
        host.reportTime(30)

        host.hideNow()

        assertThat(host.current.value).isNull()
        assertThat(host.revealed.value).isFalse()
        assertThat(host.currentTimeSec.value).isEqualTo(0L)
    }

    @Test
    fun `hideNow cancels pending debounce hide`() = runTest {
        val host = TrailerHost(this)
        val token = host.activate("item1", "key1", "YouTube")
        host.deactivate(token)

        host.hideNow()

        host.activate("item2", "key2", "YouTube")

        advanceTimeBy(600)

        assertThat(host.current.value?.itemId).isEqualTo("item2")
    }

    @Test
    fun `reportPlaying sets revealed`() = runTest {
        val host = TrailerHost(this)
        assertThat(host.revealed.value).isFalse()

        host.reportPlaying()
        assertThat(host.revealed.value).isTrue()
    }

    @Test
    fun `reportEnded clears revealed`() = runTest {
        val host = TrailerHost(this)
        host.reportPlaying()
        host.reportEnded()

        assertThat(host.revealed.value).isFalse()
    }

    @Test
    fun `reportTime updates currentTimeSec`() = runTest {
        val host = TrailerHost(this)
        host.reportTime(55)
        assertThat(host.currentTimeSec.value).isEqualTo(55L)
    }

    @Test
    fun `embeddability cache stores and retrieves`() = runTest {
        val host = TrailerHost(this)

        assertThat(host.getCachedEmbeddable("abc")).isNull()

        host.cacheEmbeddable("abc", true)
        assertThat(host.getCachedEmbeddable("abc")).isTrue()

        host.cacheEmbeddable("xyz", false)
        assertThat(host.getCachedEmbeddable("xyz")).isFalse()
    }

    @Test
    fun `debounce hide is cancelled when new claim arrives during nav gap`() = runTest {
        val host = TrailerHost(this)
        val token1 = host.activate("item1", "key1", "YouTube")
        host.reportPlaying()

        host.deactivate(token1)

        advanceTimeBy(200)
        host.activate("item2", "key1", "YouTube")

        advanceTimeBy(400)

        assertThat(host.current.value).isNotNull()
        assertThat(host.current.value?.itemId).isEqualTo("item2")
        assertThat(host.revealed.value).isTrue()
    }
}
