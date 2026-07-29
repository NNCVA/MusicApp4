package com.musicapp.player.di

import com.musicapp.player.core.common.coroutines.ApplicationCoroutineScope
import com.musicapp.player.core.common.random.RandomSource
import com.musicapp.player.core.common.time.Clock
import com.musicapp.player.fakes.FakeClock
import com.musicapp.player.fakes.FakeRandomSource
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(ApplicationModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class ApplicationGraphTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val replacementClock: Clock = FakeClock(timeMillis = 42L)

    @BindValue
    @JvmField
    val replacementRandomSource: RandomSource = FakeRandomSource(nextValue = 7)

    private val replacementJob = SupervisorJob()

    @BindValue
    @ApplicationCoroutineScope
    @JvmField
    val replacementScope: CoroutineScope = CoroutineScope(replacementJob)

    @Inject
    lateinit var clock: Clock

    @Inject
    lateinit var randomSource: RandomSource

    @Inject
    @ApplicationCoroutineScope
    lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        replacementJob.cancel()
    }

    @Test
    fun clockRandomSourceAndApplicationScopeCanBeReplaced() {
        assertSame(replacementClock, clock)
        assertSame(replacementRandomSource, randomSource)
        assertSame(replacementScope, applicationScope)
        assertEquals(42L, clock.currentTimeMillis())
        assertEquals(2, randomSource.nextInt(untilExclusive = 5))
        assertTrue(applicationScope.coroutineContext[Job]?.isActive == true)
    }
}
