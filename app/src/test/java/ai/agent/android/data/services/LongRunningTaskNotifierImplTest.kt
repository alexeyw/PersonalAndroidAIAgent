package ai.agent.android.data.services

import ai.agent.android.domain.constants.NotificationChannels
import ai.agent.android.domain.repositories.SettingsRepository
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Robolectric coverage for [LongRunningTaskNotifierImpl] — the notifier that
 * surfaces "still running" status updates for pipelines that take a noticeable
 * amount of wall-clock time. The notifier is double-gated (the per-feature
 * settings toggle + the `POST_NOTIFICATIONS` runtime permission); both gates
 * are exercised here.
 */
@RunWith(RobolectricTestRunner::class)
class LongRunningTaskNotifierImplTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsRepository
    private lateinit var notifier: LongRunningTaskNotifierImpl

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        settings = mockk()
        notifier = LongRunningTaskNotifierImpl(context, settings)
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun grantPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Shadows.shadowOf(context as android.app.Application)
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun denyPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Shadows.shadowOf(context as android.app.Application)
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun `given registerChannel is called then LONG_RUNNING_TASKS channel is created with IMPORTANCE_LOW`() {
        notifier.registerChannel()

        val channel = notificationManager().getNotificationChannel(NotificationChannels.LONG_RUNNING_TASKS)
        assertNotNull("Channel must be registered after registerChannel()", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals("Long-running tasks", channel.name)
    }

    @Test
    fun `given notifications disabled when notify is called then no notification is posted`() = runTest {
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf(false)
        grantPostNotifications()

        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 10_000L)

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given POST_NOTIFICATIONS is denied when notify is called then nothing is posted`() = runTest {
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf(true)
        denyPostNotifications()

        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 10_000L)

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given enabled and permission granted when notify is called then posts on LONG_RUNNING_TASKS`() = runTest {
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf(true)
        grantPostNotifications()
        notifier.registerChannel()

        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 12_345L)

        val shadow = Shadows.shadowOf(notificationManager())
        assertEquals(1, shadow.size())
        val posted = shadow.allNotifications.first()
        assertEquals(NotificationChannels.LONG_RUNNING_TASKS, posted.channelId)
    }

    @Test
    fun `given same pipeline name twice when notify is called then notification id is stable`() = runTest {
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf(true)
        grantPostNotifications()
        notifier.registerChannel()

        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 1_000L)
        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 2_000L)

        // The second post must replace the first via stable id, never spam.
        assertEquals(1, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given two different pipeline names when notify is called then notification ids are distinct`() = runTest {
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf(true)
        grantPostNotifications()
        notifier.registerChannel()

        notifier.notify(pipelineName = "PipelineA", elapsedMs = 1_000L)
        notifier.notify(pipelineName = "PipelineB", elapsedMs = 1_000L)

        val shadow = Shadows.shadowOf(notificationManager())
        // Two distinct pipeline names — both must be present (no replacement).
        // `size() == 2` is the load-bearing assertion here: if the ids collided,
        // the second post would have overwritten the first and size would be 1.
        assertEquals(2, shadow.size())
        assertTrue(
            "Both posted notifications must reuse the LONG_RUNNING_TASKS channel",
            shadow.allNotifications.all { it.channelId == NotificationChannels.LONG_RUNNING_TASKS },
        )
    }

    @Test
    fun `given the settings flow is empty when notify is called then nothing is posted (default disabled)`() = runTest {
        // `firstOrNull()` returns null when the flow is empty; the impl maps that
        // to "feature disabled" (?: false). Guarantees a fresh install with no
        // persisted preference stays silent.
        every { settings.longRunningTaskNotificationsEnabled } returns flowOf()
        grantPostNotifications()

        notifier.notify(pipelineName = "MyPipeline", elapsedMs = 10_000L)

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
    }
}
