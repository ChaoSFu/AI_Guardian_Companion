package com.example.ai_guardian_companion.data.local

import androidx.room.TypeConverter
import com.example.ai_guardian_companion.data.model.EmergencyType
import com.example.ai_guardian_companion.data.model.GuideMode
import com.example.ai_guardian_companion.data.model.HazardLevel

class Converters {

    @TypeConverter
    fun fromEmergencyType(value: EmergencyType): String {
        return value.name
    }

    @TypeConverter
    fun toEmergencyType(value: String): EmergencyType {
        return try {
            EmergencyType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            EmergencyType.OTHER
        }
    }

    @TypeConverter
    fun fromGuideMode(value: GuideMode): String {
        return value.name
    }

    @TypeConverter
    fun toGuideMode(value: String): GuideMode {
        return try {
            GuideMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            GuideMode.NAVIGATION
        }
    }

    @TypeConverter
    fun fromHazardLevel(value: HazardLevel?): String? {
        return value?.name
    }

    @TypeConverter
    fun toHazardLevel(value: String?): HazardLevel? {
        return value?.let {
            try {
                HazardLevel.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
