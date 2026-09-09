package com.silverchat.feature.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverchat.core.domain.repository.CallRepository
import com.silverchat.core.model.CallHistoryEntry
import com.silverchat.core.model.CallType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * История звонков.
 *
 * Фильтр «только пропущенные» — обязательная часть экрана: в длинной
 * истории найти непринятый вызов прокруткой практически невозможно,
 * а перезвонить нужно именно на него.
 *
 * Группировка по дням выполняется здесь, а не в компоузле: список
 * пересобирается при каждом новом звонке, и считать границы дней
 * на каждую recomposition — лишняя работа в UI-потоке.
 */
@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    callRepository: CallRepository,
) : ViewModel() {

    /** Фильтр экрана: все звонки или только пропущенные. */
    private val missedOnly = MutableStateFlow(false)

    val uiState: StateFlow<CallHistoryUiState> = combine(
        callRepository.observeCallHistory(),
        missedOnly,
    ) { entries, onlyMissed ->
        val filtered = if (onlyMissed) entries.filter { it.missed } else entries
        CallHistoryUiState(
            groups = groupByDay(filtered),
            missedOnly = onlyMissed,
            totalMissed = entries.count { it.missed },
            totalCount = entries.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CallHistoryUiState(),
    )

    fun toggleMissedOnly() {
        missedOnly.value = !missedOnly.value
    }

    /**
     * Группировка по дням.
     *
     * История приходит отсортированной по убыванию времени, поэтому
     * достаточно проходить список один раз и закрывать группу при смене дня.
     */
    private fun groupByDay(entries: List<CallHistoryEntry>): List<CallDayGroup> {
        if (entries.isEmpty()) return emptyList()
        val groups = mutableListOf<CallDayGroup>()
        var currentDay = -1L
        var bucket = mutableListOf<CallHistoryEntry>()

        entries.sortedByDescending { it.startedAt }.forEach { entry ->
            val day = entry.startedAt / DAY_MS
            if (day != currentDay) {
                if (bucket.isNotEmpty()) groups += CallDayGroup(currentDay, bucket.toList())
                currentDay = day
                bucket = mutableListOf()
            }
            bucket += entry
        }
        if (bucket.isNotEmpty()) groups += CallDayGroup(currentDay, bucket.toList())
        return groups
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

data class CallDayGroup(
    val day: Long,
    val entries: List<CallHistoryEntry>,
) {
    /** Первый элемент группы — для заголовка «Сегодня» / «Вчера» / дата. */
    val firstStartedAt: Long get() = entries.firstOrNull()?.startedAt ?: 0L
}

data class CallHistoryUiState(
    val groups: List<CallDayGroup> = emptyList(),
    val missedOnly: Boolean = false,
    val totalMissed: Int = 0,
    val totalCount: Int = 0,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/** Звонок был видеозвонком — иконка в строке истории отличается. */
val CallHistoryEntry.isVideo: Boolean
    get() = type == CallType.VIDEO || type == CallType.GROUP_VIDEO

/** Русская подпись типа звонка для строки истории. */
val CallHistoryEntry.typeRu: String
    get() = when (type) {
        CallType.AUDIO -> "Голосовой звонок"
        CallType.VIDEO -> "Видеозвонок"
        CallType.GROUP_AUDIO -> "Групповой звонок"
        CallType.GROUP_VIDEO -> "Групповой видеозвонок"
    }
