@file:Suppress("unused")

package org.icpclive.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.icpclive.cds.ProblemInfo as CDSProblemsInfo

@Serializable
data class Advertisement(val text: String)

@Serializable
data class Picture(val url: String, val name: String)

@Serializable
class QueueSettings() // TODO??

@Serializable
data class RunInfo(
    val id: Int,
    val isAccepted: Boolean,
    val isJudged: Boolean,
    val result: String,
    val problemId: Int,
    val teamId: Int,
    val isReallyUnknown: Boolean,
    val percentage: Double,
    val time: Long,
    val lastUpdateTime: Long,
    val isFirstSolvedRun: Boolean,
)

@Serializable
data class ProblemInfo(val letter: String, val name: String, val color: String) {
    constructor(info: CDSProblemsInfo) :
            this(info.letter, info.name, "#" + "%08x".format(info.color.rgb))
}

fun CDSProblemsInfo.toApi() = ProblemInfo(this)

@Serializable
enum class ContestStatus {
    UNKNOWN, BEFORE, RUNNING, PAUSED, OVER
}

@Serializable
data class ContestInfo(
    val status: ContestStatus,
    val startTimeUnixMs: Long,
    val contestLengthMs: Long,
    val freezeTimeMs: Long,
    val problems: List<ProblemInfo>,
    val teams: List<TeamInfo>
) {
    companion object {
        val EMPTY = ContestInfo(ContestStatus.UNKNOWN, 0, 0, 0, emptyList(), emptyList())
    }
}

@Serializable
sealed class ProblemResult

//TODO: custom string, problem with score, maybe something else
@Serializable
@SerialName("icpc")
data class ICPCProblemResult(
    val wrongAttempts: Int,
    val pendingAttempts: Int,
    val isSolved: Boolean,
    val isFirstToSolve: Boolean,
) : ProblemResult()


@Serializable
data class TeamInfo(
    val id: Int,
    val rank: Int,
    val name: String,
    val shortName: String?,
    val alias: String?,
    val groups: List<String>,
    val penalty: Int,
    val solvedProblemsNumber: Int,
    val lastAccepted: Long,
    val hashTag: String?,
)

@Serializable
data class Scoreboard(val rows: List<ScoreboardRow>)

@Serializable
data class ScoreboardRow(
    val teamId: Int,
    val rank: Int,
    val totalScore: Int,
    val penalty: Int,
    val problemResults: List<ProblemResult>,
)