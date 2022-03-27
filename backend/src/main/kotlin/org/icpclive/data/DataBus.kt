package org.icpclive.data

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.icpclive.api.*
import org.icpclive.cds.OptimismLevel
import org.icpclive.utils.firstNotNull

/**
 * Everything published here should be immutable, to allow secure work from many threads
 *
 * So for now, we can't do flow of contestInfo, we need to refactor it first.
 * Only runs are published now, with copy of list to make this data immutable
 */
object DataBus {
    val contestInfoUpdates = MutableStateFlow(ContestInfo.EMPTY)
    private val mainScreenEventsHolder = MutableStateFlow<Flow<MainScreenEvent>?>(null)
    private val queueEventsFlowHolder = MutableStateFlow<Flow<QueueEvent>?>(null)
    private val tickerFlowHolder = MutableStateFlow<Flow<TickerEvent>?>(null)
    private val scoreboardFlowHolders = Array(OptimismLevel.values().size) { MutableStateFlow<Flow<Scoreboard>?>(null) }
    private val statisticFlowHolder = MutableStateFlow<Flow<SolutionsStatistic>?>(null)

    fun setMainScreenEvents(value: Flow<MainScreenEvent>) { mainScreenEventsHolder.value = value }
    fun setQueueEvents(value: Flow<QueueEvent>) { queueEventsFlowHolder.value = value }
    fun setStatisticEvents(value: Flow<SolutionsStatistic>) { statisticFlowHolder.value = value }
    fun setScoreboardEvents(level: OptimismLevel, flow: Flow<Scoreboard>) {
        scoreboardFlowHolders[level.ordinal].value = flow
    }
    fun setTickerEvents(value: Flow<TickerEvent>)  { tickerFlowHolder.value = value }

    suspend fun mainScreenEvents() = mainScreenEventsHolder.firstNotNull()
    suspend fun queueEvents() = queueEventsFlowHolder.firstNotNull()
    suspend fun tickerEvents() = tickerFlowHolder.firstNotNull()
    suspend fun statisticEvents() = statisticFlowHolder.firstNotNull()
    suspend fun scoreboardEvents(level: OptimismLevel) = scoreboardFlowHolders[level.ordinal].firstNotNull()

    @OptIn(FlowPreview::class)
    val allEvents
        get() = listOf(mainScreenEventsHolder, queueEventsFlowHolder, tickerFlowHolder)
            .map { it.filterNotNull().take(1) }
            .merge()
            .flattenMerge(concurrency = Int.MAX_VALUE)
}
