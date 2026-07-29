package app.knotwork.android.domain.usecases

import app.knotwork.android.domain.services.TaskScheduler
import javax.inject.Inject

/**
 * Cancels every task the agent scheduled for itself — the user-facing escape
 * hatch from a task that keeps re-scheduling itself.
 *
 * Kept as its own use case rather than calling the scheduler from the UI so the
 * blast radius is stated in one place: this settles tasks created by the
 * `schedule_task` tool and nothing else. Automation triggers keep their own
 * lifecycle (and their own delete action), and a run started from the
 * Quick-Settings tile is a deliberate one-off — folding either into "cancel all"
 * would turn a recovery action into a second surprise.
 */
class CancelScheduledTasksUseCase @Inject constructor(private val taskScheduler: TaskScheduler) {

    /** Cancels all scheduled agent tasks, queued and running alike. */
    operator fun invoke() {
        taskScheduler.cancelAllScheduled()
    }
}
