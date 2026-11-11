package com.mohit.nudge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class NudgeSettings(
    val breakfastMin: Int = 9 * 60,
    val lunchMin: Int = 12 * 60 + 30,
    val dinnerMin: Int = 20 * 60,
    val beforeOffsetMin: Int = 30,
    val afterOffsetMin: Int = 15,
    val renotifyMin: Int = 10,
    val giveUpMin: Int = 60,
    /** Evening-before heads-up for non-daily medicines. */
    val headsUpEnabled: Boolean = true,
    val headsUpMin: Int = 21 * 60,
    /** Evening slot (between lunch and dinner), minute of day. */
    val eveningMin: Int = 17 * 60,
) {
    fun mealMinute(meal: Meal): Int = when (meal) {
        Meal.BREAKFAST -> breakfastMin
        Meal.LUNCH -> lunchMin
        Meal.DINNER -> dinnerMin
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val breakfast = intPreferencesKey("breakfast_min")
        val lunch = intPreferencesKey("lunch_min")
        val dinner = intPreferencesKey("dinner_min")
        val before = intPreferencesKey("before_offset_min")
        val after = intPreferencesKey("after_offset_min")
        val renotify = intPreferencesKey("renotify_min")
        val giveUp = intPreferencesKey("give_up_min")
        val headsUpEnabled = booleanPreferencesKey("heads_up_enabled")
        val headsUpMin = intPreferencesKey("heads_up_min")
        val evening = intPreferencesKey("evening_min")
        fun meal(meal: Meal) = when (meal) {
            Meal.BREAKFAST -> breakfast
            Meal.LUNCH -> lunch
            Meal.DINNER -> dinner
        }
    }

    val flow: Flow<NudgeSettings> = context.dataStore.data.map { p ->
        val d = NudgeSettings()
        NudgeSettings(
            breakfastMin = p[Keys.breakfast] ?: d.breakfastMin,
            lunchMin = p[Keys.lunch] ?: d.lunchMin,
            dinnerMin = p[Keys.dinner] ?: d.dinnerMin,
            beforeOffsetMin = p[Keys.before] ?: d.beforeOffsetMin,
            afterOffsetMin = p[Keys.after] ?: d.afterOffsetMin,
            renotifyMin = p[Keys.renotify] ?: d.renotifyMin,
            giveUpMin = p[Keys.giveUp] ?: d.giveUpMin,
            headsUpEnabled = p[Keys.headsUpEnabled] ?: d.headsUpEnabled,
            headsUpMin = p[Keys.headsUpMin] ?: d.headsUpMin,
            eveningMin = p[Keys.evening] ?: d.eveningMin,
        )
    }

    suspend fun get(): NudgeSettings = flow.first()

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setMealTime(meal: Meal, minuteOfDay: Int) = edit { it[Keys.meal(meal)] = minuteOfDay }
    suspend fun setBeforeOffset(min: Int) = edit { it[Keys.before] = min }
    suspend fun setAfterOffset(min: Int) = edit { it[Keys.after] = min }
    suspend fun setRenotify(min: Int) = edit { it[Keys.renotify] = min }
    suspend fun setGiveUp(min: Int) = edit { it[Keys.giveUp] = min }
    suspend fun setHeadsUpEnabled(on: Boolean) = edit { it[Keys.headsUpEnabled] = on }
    suspend fun setHeadsUpTime(minuteOfDay: Int) = edit { it[Keys.headsUpMin] = minuteOfDay }
    suspend fun setEveningTime(minuteOfDay: Int) = edit { it[Keys.evening] = minuteOfDay }
}
