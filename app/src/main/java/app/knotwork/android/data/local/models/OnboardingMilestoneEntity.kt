package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for one write-once marker on the install → first-value onboarding
 * path, backing the repeatable "< 10 minutes to first value" measurement.
 *
 * The marker key is the primary key and rows are written with `INSERT OR IGNORE`,
 * so the table holds the **first** occurrence of each marker and a repeated
 * onboarding can never move an already-measured journey.
 *
 * Unlike the aggregate counters next door, these rows do carry wall-clock
 * timestamps — the whole point is measuring durations. That is no new exposure:
 * they live in the same SQLCipher-encrypted database that already stores full
 * per-run timestamps, and nothing here ever leaves the device. Added in schema
 * v52 (`MIGRATION_51_52`).
 *
 * @property milestoneKey Stable `OnboardingMilestone.name` (never renamed).
 * @property atMillis Device wall-clock time the marker was reached, epoch-millis.
 * @property detail Optional payload — the materialised pipeline id for
 *   `SCENARIO_CHOSEN` (which scopes first-value attribution), `null` otherwise.
 */
@Entity(tableName = "onboarding_milestone")
data class OnboardingMilestoneEntity(@PrimaryKey val milestoneKey: String, val atMillis: Long, val detail: String?)
