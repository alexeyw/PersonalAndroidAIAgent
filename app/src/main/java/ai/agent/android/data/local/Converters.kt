package ai.agent.android.data.local

import androidx.room.TypeConverter

/**
 * Provides TypeConverters for Room to handle custom data types.
 * Specifically handles the conversion between [FloatArray] and [String]
 * to store vector embeddings in the SQLite database.
 */
class Converters {

    /**
     * Converts a [FloatArray] to a comma-separated [String].
     *
     * @param array The float array to convert.
     * @return A comma-separated string representation of the array, or null if the array is null.
     */
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String? {
        return array?.joinToString(separator = ",")
    }

    /**
     * Converts a comma-separated [String] back into a [FloatArray].
     *
     * @param value The string to convert.
     * @return The resulting [FloatArray], or null if the input is null or empty.
     */
    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        if (value.isNullOrBlank()) return null
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }
}
