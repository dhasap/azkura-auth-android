package id.azkura.auth.data.local.prefs

/**
 * Persistent account sorting options.
 *
 * [storedValue] is the stable DataStore value. [label] is the text shown in UI.
 */
enum class SortOrder(
    val storedValue: String,
    val label: String,
) {
    CUSTOM("custom", "Custom"),
    ALPHABETICAL("alphabetical", "Alphabetical"),
    MOST_USED("most_used", "Most Used"),
    RECENTLY_ADDED("recently_added", "Recently Added"),
    ;

    companion object {
        val DEFAULT = CUSTOM

        fun fromStoredValue(value: String?): SortOrder {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { order ->
                order.storedValue.equals(normalized, ignoreCase = true) ||
                    order.name.equals(normalized, ignoreCase = true) ||
                    order.label.equals(normalized, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}
