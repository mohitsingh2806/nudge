package com.mohit.nudge.domain

import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.RepeatType

/**
 * Medicines to mention in the evening-before heads-up sent on [todayEpochDay]:
 * non-daily, due tomorrow, and NOT due today (if it was due today you just took it,
 * so a reminder tonight would be noise).
 */
fun headsUpItems(todayEpochDay: Long, meds: List<Medicine>): List<Medicine> =
    meds.filter {
        it.repeatType() != RepeatType.DAILY &&
            it.isDueOn(todayEpochDay + 1) &&
            !it.isDueOn(todayEpochDay)
    }
