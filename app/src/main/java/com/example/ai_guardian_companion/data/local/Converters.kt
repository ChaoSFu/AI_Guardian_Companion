package com.example.ai_guardian_companion.data.local

import androidx.room.TypeConverter
import com.example.ai_guardian_companion.data.model.EmergencyType

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
}
