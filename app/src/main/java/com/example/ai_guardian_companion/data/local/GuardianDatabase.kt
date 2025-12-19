package com.example.ai_guardian_companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ai_guardian_companion.data.local.dao.*
import com.example.ai_guardian_companion.data.model.*

@Database(
    entities = [
        UserProfile::class,
        FamilyMember::class,
        MedicationReminder::class,
        LocationLog::class,
        EmergencyEvent::class,
        GuideMessage::class,
        GuideSession::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GuardianDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun medicationReminderDao(): MedicationReminderDao
    abstract fun locationLogDao(): LocationLogDao
    abstract fun emergencyEventDao(): EmergencyEventDao
    abstract fun guideMessageDao(): GuideMessageDao
    abstract fun guideSessionDao(): GuideSessionDao

    companion object {
        @Volatile
        private var INSTANCE: GuardianDatabase? = null

        fun getDatabase(context: Context): GuardianDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
