package com.mohit.nudge.data

enum class Meal(val label: String, val period: String) {
    BREAKFAST("Breakfast", "Morning"),
    LUNCH("Lunch", "Noon"),
    DINNER("Dinner", "Night"),
}

enum class Timing { BEFORE, AFTER }

/**
 * Reminder slots: {Morning, Noon, Night} x {Before food, After food}, plus Evening at its own time.
 *
 * Append-only: [bit] (saved in Medicine.slotMask) and alarm / notification ids come from the
 * ordinal, so inserting an entry in the middle would scramble saved medicines. Use [displayOrder]
 * for showing slots in the order of the day.
 */
enum class Slot(val meal: Meal?, val timing: Timing?) {
    MORNING_BEFORE(Meal.BREAKFAST, Timing.BEFORE),
    MORNING_AFTER(Meal.BREAKFAST, Timing.AFTER),
    NOON_BEFORE(Meal.LUNCH, Timing.BEFORE),
    NOON_AFTER(Meal.LUNCH, Timing.AFTER),
    NIGHT_BEFORE(Meal.DINNER, Timing.BEFORE),
    NIGHT_AFTER(Meal.DINNER, Timing.AFTER),

    /** Between lunch and dinner at a fixed time from settings; not tied to food. */
    EVENING(null, null);

    val bit: Int get() = 1 shl ordinal

    /** e.g. "Before breakfast", "Evening" */
    val label: String
        get() = if (meal == null) {
            "Evening"
        } else {
            (if (timing == Timing.BEFORE) "Before " else "After ") + meal.label.lowercase()
        }

    companion object {
        /** Slots in the order of a normal day. */
        val displayOrder: List<Slot> = listOf(
            MORNING_BEFORE, MORNING_AFTER, NOON_BEFORE, NOON_AFTER, EVENING, NIGHT_BEFORE, NIGHT_AFTER,
        )

        fun fromMask(mask: Int): List<Slot> = displayOrder.filter { mask and it.bit != 0 }
        fun maskOf(slots: Collection<Slot>): Int = slots.fold(0) { acc, s -> acc or s.bit }
        fun of(meal: Meal, timing: Timing): Slot = entries.first { it.meal == meal && it.timing == timing }
    }
}
