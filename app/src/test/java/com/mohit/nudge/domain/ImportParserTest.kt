package com.mohit.nudge.domain

import com.mohit.nudge.data.RepeatType
import com.mohit.nudge.data.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImportParserTest {
    private val colors = listOf(1, 2, 3)

    private fun parse(text: String) = ImportParser.parse(text, startEpochDay = 100, colors = colors)

    @Test
    fun parsesPromptExamplesAndIgnoresChatter() {
        val lines = parse(
            """
            Here are your medicines:
            ```
            Pantocid 40 | 1 tablet | BB | daily | ongoing
            Dolo 650 | 1 tablet | AB+AD | daily | 5
            Vitamin D3 60K | 1 capsule | AB | Sun | 8 weeks
            Ecosprin 75 | 1 tablet | AL | alternate days | ongoing
            ```
            """.trimIndent(),
        )
        assertEquals(4, lines.size)
        lines.forEach { assertNull(it.error, it.error) }
        val (panto, dolo, vitd, eco) = lines.map { it.medicine!! }

        assertEquals("Pantocid 40", panto.name)
        assertEquals(listOf(Slot.MORNING_BEFORE), panto.slots())
        assertNull(panto.durationDays)
        assertEquals(RepeatType.DAILY, panto.repeatType())
        assertEquals(100L, panto.startEpochDay)

        assertEquals(listOf(Slot.MORNING_AFTER, Slot.NIGHT_AFTER), dolo.slots())
        assertEquals(5, dolo.durationDays)

        assertEquals(RepeatType.WEEKDAYS, vitd.repeatType())
        assertEquals(56, vitd.durationDays)

        assertEquals(2, eco.intervalDays)

        // colours cycle
        assertEquals(listOf(1, 2, 3, 1), listOf(panto, dolo, vitd, eco).map { it.color })
    }

    @Test
    fun unclearAndBadLinesBecomeErrors() {
        val lines = parse(
            """
            Name | Dose | When | How often | Days
            Xyz | ?? | BB | daily | 5
            Abc | 1 tab | 1-0-1 | daily | 5
            Def | 1 tab | AD | twice a week | ongoing
            """.trimIndent(),
        )
        assertEquals(3, lines.size) // header skipped
        lines.forEach { assertNull(it.medicine); assertNotNull(it.error) }
    }

    @Test
    fun phrasesAndUnits() {
        assertEquals(
            ImportParser.Parsed.Ok(setOf(Slot.MORNING_BEFORE, Slot.NIGHT_AFTER)),
            ImportParser.parseSlots("before breakfast + after dinner"),
        )
        assertEquals(ImportParser.Parsed.Ok(21 to 0), ImportParser.parseRepeat("every 3 weeks"))
    }

    @Test
    fun foodIndependentTimesUseAfterFoodSlot() {
        fun slots(s: String) = (ImportParser.parseSlots(s) as ImportParser.Parsed.Ok).value
        assertEquals(setOf(Slot.MORNING_AFTER), slots("ANY"))
        assertEquals(setOf(Slot.MORNING_AFTER), slots("once daily"))
        assertEquals(setOf(Slot.MORNING_AFTER), slots("morning"))
        assertEquals(setOf(Slot.NOON_AFTER), slots("afternoon"))
        assertEquals(setOf(Slot.NIGHT_AFTER), slots("bedtime"))
        assertEquals(setOf(Slot.NIGHT_AFTER), slots("HS"))
        assertEquals(setOf(Slot.MORNING_AFTER, Slot.NIGHT_AFTER), slots("morning + night"))
        // explicit food words still win
        assertEquals(setOf(Slot.NIGHT_BEFORE), slots("before dinner"))
        assertEquals(setOf(Slot.MORNING_BEFORE), slots("empty stomach"))
        assertEquals(ImportParser.Parsed.Ok(30), ImportParser.parseDays("1 month"))
        assertEquals(ImportParser.Parsed.Ok(null), ImportParser.parseDays(""))
    }

    @Test
    fun eveningSlot() {
        fun slots(s: String) = (ImportParser.parseSlots(s) as ImportParser.Parsed.Ok).value
        assertEquals(setOf(Slot.EVENING), slots("EV"))
        assertEquals(setOf(Slot.EVENING), slots("evening"))
        assertEquals(setOf(Slot.MORNING_AFTER, Slot.EVENING), slots("AB+EV"))
        // A dinner word keeps it a dinner slot.
        assertEquals(setOf(Slot.NIGHT_AFTER), slots("after dinner in the evening"))
    }
}
