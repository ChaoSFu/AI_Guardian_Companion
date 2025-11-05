package com.example.ai_guardian_companion.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_guardian_companion.GuardianApplication
import com.example.ai_guardian_companion.data.model.*
import com.example.ai_guardian_companion.data.repository.*
import com.example.ai_guardian_companion.utils.LocationHelper
import com.example.ai_guardian_companion.utils.NotificationHelper
import com.example.ai_guardian_companion.utils.TTSHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主 ViewModel
 * 管理应用的核心状态和业务逻辑
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as GuardianApplication).database

    // Repositories
    private val userRepository = UserRepository(database.userProfileDao())
    private val familyRepository = FamilyRepository(database.familyMemberDao())
    private val reminderRepository = ReminderRepository(database.medicationReminderDao())
    private val locationRepository = LocationRepository(database.locationLogDao())
    private val emergencyRepository = EmergencyRepository(database.emergencyEventDao())

    // Helpers
    val ttsHelper = TTSHelper(application)
    private val notificationHelper = NotificationHelper(application)
    private val locationHelper = LocationHelper(application)

    // State Flows
    val userProfile: StateFlow<UserProfile?> = userRepository.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val familyMembers: StateFlow<List<FamilyMember>> = familyRepository.getAllFamilyMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeReminders: StateFlow<List<MedicationReminder>> = reminderRepository.getActiveReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unresolvedEmergencies: StateFlow<List<EmergencyEvent>> = emergencyRepository.getUnresolvedEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentLocation = MutableStateFlow<android.location.Location?>(null)
    val currentLocation: StateFlow<android.location.Location?> = _currentLocation

    private val _isEmergencyMode = MutableStateFlow(false)
    val isEmergencyMode: StateFlow<Boolean> = _isEmergencyMode

    init {
        startLocationTracking()
    }

    /**
     * 保存用户档案
     */
    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            userRepository.saveUserProfile(profile)
        }
    }

    /**
     * 添加家人
     */
    fun addFamilyMember(member: FamilyMember) {
        viewModelScope.launch {
            familyRepository.addFamilyMember(member)
            ttsHelper.speak("已添加家人 ${member.name}")
        }
    }

    /**
     * 添加服药提醒
     */
    fun addMedicationReminder(reminder: MedicationReminder) {
        viewModelScope.launch {
            reminderRepository.addReminder(reminder)
            ttsHelper.speak("已设置服药提醒：${reminder.medicationName}")
        }
    }

    /**
     * 标记服药完成
     */
    fun markMedicationTaken(reminderId: Long) {
        viewModelScope.launch {
            reminderRepository.markAsTaken(reminderId)
            notificationHelper.sendGeneralNotification("服药记录", "已记录本次服药")
            ttsHelper.speak("好的，已记录")
        }
    }

    /**
     * 开始位置追踪
     */
    private fun startLocationTracking() {
        viewModelScope.launch {
            locationHelper.getLocationUpdates().collect { location ->
                _currentLocation.value = location
                // 记录位置
                locationRepository.logLocation(
                    LocationLog(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy
                    )
                )
            }
        }
    }

    /**
     * 触发紧急模式
     */
    fun triggerEmergency(type: EmergencyType, description: String = "") {
        viewModelScope.launch {
            _isEmergencyMode.value = true

            val location = _currentLocation.value
            val event = EmergencyEvent(
                eventType = type,
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                description = description
            )

            emergencyRepository.createEvent(event)

            // 通知家人
            val primaryContact = familyRepository.getPrimaryContact().first()
            primaryContact?.let {
                notificationHelper.sendEmergencyAlert(
                    "紧急情况",
                    "用户可能需要帮助：$description"
                )
            }

            // 语音安抚
            ttsHelper.speakUrgent("请稍等，我已经通知家人了，他们很快就会来帮助你")
        }
    }

    /**
     * 解除紧急模式
     */
    fun resolveEmergency(eventId: Long) {
        viewModelScope.launch {
            emergencyRepository.resolveEvent(eventId)
            _isEmergencyMode.value = false
            ttsHelper.speak("紧急情况已解除")
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
    }
}
