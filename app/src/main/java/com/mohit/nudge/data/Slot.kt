package com.mohit.nudge.data

enum class Meal(val label: String, val period: String) {
    BREAKFAST("Breakfast", "Morning"),
    LUNCH("Lunch", "Noon"),
    DINNER("Dinner", "Night"),
}

enum class Timing { BEFORE, AFTER }

/** The six reminder slots: {Morning, Noon, Night} x {Before food, After food}. */
enum class Slot(val meal: Meal, val timing: Timing) {
    MORNING_BEFORE(Meal.BREAKFAST, Timing.BEFORE),
    MORNING_AFTER(Meal.BREAKFAST, Timing.AFTER),
    NOON_BEFORE(Meal.LUNCH, Timing.BEFORE),
    NOON_AFTER(Meal.LUNCH, Timing.AFTER),
    NIGHT_BEFORE(Meal.DINNER, Timing.BEFORE),
    NIGHT_AFTER(Meal.DINNER, Timing.AFTER);

    val bit: Int get() = 1 shl ordinal

    /** e.g. "Before breakfast" */
    val label: String
        get() = (if (timing == Timing.BEFORE) "Before " else "After ") + meal.label.lowercase()

    companion object {
        fun fromMask(mask: Int): List<Slot> = entries.filter { mask and it.bit != 0 }
        fun maskOf(slots: Collection<Slot>): Int = slots.fold(0) { acc, s -> acc or s.bit }
        fun of(meal: Meal, timing: Timing): Slot = entries.first { it.meal == meal && it.timing == timing }
    }
}
