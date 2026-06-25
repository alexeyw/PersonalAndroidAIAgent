package app.knotwork.android.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity recording one distinct device-local calendar day on which usage
 * occurred — the privacy-preserving daily-active proxy of the local telemetry
 * feature.
 *
 * Exactly one row per active day (the ISO `yyyy-MM-dd` string is the primary
 * key), written via `INSERT OR IGNORE`, so the table holds only the **set** of
 * active days, never per-event timestamps. The daily-active figure is therefore
 * just `COUNT(*)`. Stored in the SQLCipher-encrypted database; never transmitted.
 *
 * Added in schema v47 (`MIGRATION_46_47`).
 *
 * @property day Device-local active day as an ISO `yyyy-MM-dd` string.
 */
@Entity(tableName = "usage_active_day")
data class UsageActiveDayEntity(
    @PrimaryKey
    val day: String,
)
