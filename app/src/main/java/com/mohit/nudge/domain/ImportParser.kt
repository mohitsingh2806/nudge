package com.mohit.nudge.domain

import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.Slot
import com.mohit.nudge.data.Timing
import java.time.DayOfWeek

/** One non-empty line of pasted text: either a parsed medicine or an error to show the user. */
data class ImportLine(val lineNo: Int, val raw: String, val medicine: Medicine?, val error: String?)

/**
 * Parses lines in the format produced by [AI_PROMPT]:
 *   Name | Dose | When | How often | Days
 * Lines without a "|" (chatty text from the AI, code fences) are ignored.
 */
object ImportParser {

    val AI_PROMPT = """
        Convert my medicines into this exact format. One medicine per line. Reply with ONLY the lines, no other text.

        Name | Dose | When | How often | Days

        When = one or more of these codes joined with +
          BB = before breakfast, AB = after breakfast
          BL = before lunch,     AL = after lunch
          BD = before dinner,    AD = after dinner
          ANY = once a day, any time (taken in the morning)
          ("empty stomach" in the morning = BB, "with food" = after. 1-0-1 means morning and night.)
          If food timing is not mentioned or doesn't matter: morning = AB, afternoon = AL,
          night / bedtime = AD, once daily = ANY. Do NOT write ?? just because food timing is missing.
          Do NOT copy prescription abbreviations: on prescriptions OD = once daily, BD/BID = twice daily
          (use AB+AD), TDS/TID = three times daily (use AB+AL+AD), HS = bedtime (use AD).
        How often = daily, alternate days, every N days, weekly, or weekday names like Mon Thu
        Days = how many days to take it (e.g. 5, 2 weeks, 1 month) or ongoing

        Examples:
        Pantocid 40 | 1 tablet | BB | daily | ongoing
        Dolo 650 | 1 tablet | AB+AD | daily | 5
        Vitamin D3 60K | 1 capsule | AB | Sun | 8 weeks
        Ecosprin 75 | 1 tablet | AL | alternate days | ongoing

        If anything is unclear or unreadable, write ?? in that field instead of guessing.
    """.trimIndent()

    private val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s+""")

    fun parse(text: String, startEpochDay: Long, colors: List<Int>): List<ImportLine> {
        val out = mutableListOf<ImportLine>()
        var colorIdx = 0
        text.lines().forEachIndexed { i, rawLine ->
            val line = rawLine.replace(bullet, "").trim().trim('|').trim()
            if (!rawLine.contains('|') || line.isEmpty()) return@forEachIndexed
            val f = line.split('|').map { it.trim() }
            if (f[0].equals("name", ignoreCase = true)) return@forEachIndexed // header row

            val error = when {
                f.any { it.contains("??") } -> "Marked unclear (??). Check the prescription and fix this line."
                f[0].isBlank() -> "Missing medicine name."
                else -> null
            }
            if (error != null) {
                out += ImportLine(i + 1, line, null, error)
                return@forEachIndexed
            }

            val slots = parseSlots(f.getOrElse(2) { "" })
            val repeat = parseRepeat(f.getOrElse(3) { "" })
            val days = parseDays(f.getOrElse(4) { "" })
            if (slots !is Parsed.Ok || repeat !is Parsed.Ok || days !is Parsed.Ok) {
                val msg = listOf(slots, repeat, days).filterIsInstance<Parsed.Err>().first().message
                out += ImportLine(i + 1, line, null, msg)
                return@forEachIndexed
            }

            val (interval, weekdayMask) = repeat.value
            val medicine = Medicine(
                name = f[0],
                dose = f.getOrElse(1) { "" },
                color = colors[colorIdx++ % colors.size],
                slotMask = Slot.maskOf(slots.value),
                startEpochDay = startEpochDay,
                durationDays = days.value,
                intervalDays = interval,
                weekdayMask = weekdayMask,
            )
            out += ImportLine(i + 1, line, medicine, null)
        }
        return out
    }

    sealed interface Parsed<out T> {
        data class Ok<T>(val value: T) : Parsed<T>
        data class Err(val message: String) : Parsed<Nothing>
    }

    private val slotCodes = mapOf(
        "bb" to Slot.MORNING_BEFORE, "ab" to Slot.MORNING_AFTER,
        "bl" to Slot.NOON_BEFORE, "al" to Slot.NOON_AFTER,
        "bd" to Slot.NIGHT_BEFORE, "ad" to Slot.NIGHT_AFTER,
        // Food timing doesn't matter: once a day, morning.
        "any" to Slot.MORNING_AFTER, "anytime" to Slot.MORNING_AFTER,
        "hs" to Slot.NIGHT_AFTER,
    )

    fun parseSlots(s: String): Parsed<Set<Slot>> {
        if (s.isBlank()) return Parsed.Err("Missing \"When\" (use BB, AB, BL, AL, BD, AD).")
        val parts = s.lowercase().split('+', ',', '&', '/', ';').flatMap { it.split(" and ") }
            .map { it.trim() }.filter { it.isNotEmpty() }
        val slots = mutableSetOf<Slot>()
        for (p in parts) {
            slots += slotCodes[p.replace(" ", "")] ?: slotFromPhrase(p)
                ?: return Parsed.Err("Don't understand \"$p\". Use BB, AB, BL, AL, BD, AD.")
        }
        return Parsed.Ok(slots)
    }

    /**
     * "before breakfast", "after dinner", "empty stomach" …
     * With no food word ("morning", "night", "bedtime"), food doesn't matter, so the
     * after-food slot for that time of day is used. "once daily" / "any time" = morning.
     */
    private fun slotFromPhrase(p: String): Slot? {
        val timing = when {
            "before" in p || "empty" in p -> Timing.BEFORE
            "after" in p || "with food" in p -> Timing.AFTER
            else -> null
        }
        val meal = when {
            "breakfast" in p || "morning" in p -> Meal.BREAKFAST
            "lunch" in p || "noon" in p || "afternoon" in p -> Meal.LUNCH
            "dinner" in p || "night" in p || "evening" in p || "bed" in p -> Meal.DINNER
            "empty" in p -> Meal.BREAKFAST
            timing == null && ("once" in p || "any" in p || "daily" in p) -> return Slot.MORNING_AFTER
            else -> return null
        }
        return Slot.of(meal, timing ?: Timing.AFTER)
    }

    private val dayNames = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
    )

    private val everyN = Regex("""every\s+(\d+)\s*(day|days|week|weeks)""")

    /** Returns (intervalDays, weekdayMask). */
    fun parseRepeat(s: String): Parsed<Pair<Int, Int>> {
        val t = s.lowercase().trim()
        val days = t.split(Regex("[^a-z]+")).mapNotNull { dayNames[it] }.toSet()
        if (days.isNotEmpty()) {
            return Parsed.Ok(1 to days.fold(0) { acc, d -> acc or Medicine.weekdayBit(d) })
        }
        everyN.find(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            val interval = if (m.groupValues[2].startsWith("week")) n * 7 else n
            if (interval < 1) return Parsed.Err("\"$s\": interval must be at least 1 day.")
            return Parsed.Ok(interval to 0)
        }
        return when {
            t.isEmpty() || "daily" in t || "every day" in t || "everyday" in t || "once a day" in t ->
                Parsed.Ok(1 to 0)
            "alternate" in t || "every other" in t -> Parsed.Ok(2 to 0)
            "fortnight" in t -> Parsed.Ok(14 to 0)
            "twice a week" in t || "biweekly" in t -> Parsed.Err("\"$s\": which days? Write weekday names, e.g. Mon Thu.")
            "weekly" in t || "once a week" in t -> Parsed.Ok(7 to 0)
            else -> Parsed.Err("Don't understand how often: \"$s\".")
        }
    }

    private val durationRegex = Regex("""^(\d+)\s*(d|day|days|w|wk|wks|week|weeks|m|month|months)?$""")

    /** Number of days, or null for ongoing. */
    fun parseDays(s: String): Parsed<Int?> {
        val t = s.lowercase().trim()
        if (t.isEmpty() || "ongoing" in t || "continue" in t || "long term" in t) return Parsed.Ok(null)
        val m = durationRegex.find(t) ?: return Parsed.Err("Don't understand duration: \"$s\".")
        val n = m.groupValues[1].toInt()
        if (n < 1) return Parsed.Err("Duration must be at least 1 day.")
        val unit = m.groupValues[2]
        val total = when {
            unit.startsWith("w") -> n * 7
            unit.startsWith("m") -> n * 30
            else -> n
        }
        return Parsed.Ok(total)
    }
}
