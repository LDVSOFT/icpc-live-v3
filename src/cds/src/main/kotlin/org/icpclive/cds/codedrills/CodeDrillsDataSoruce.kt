package org.icpclive.cds.codedrills

import io.codedrills.proto.external.*
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import kotlinx.datetime.Instant
import org.icpclive.api.*
import org.icpclive.cds.common.ContestParseResult
import org.icpclive.cds.common.FullReloadContestDataSource
import org.icpclive.cds.settings.CodeDrillsSettings
import org.icpclive.util.getLogger
import java.util.concurrent.*
import kotlin.time.Duration.Companion.seconds

internal class CodeDrillsClient(url: String, port: Int, authKey: String) : AutoCloseable {
    private val channel = ManagedChannelBuilder.forAddress(url, port).usePlaintext().build()
    private val stub = ContestServiceGrpcKt.ContestServiceCoroutineStub(channel).let {
        val metadata = Metadata()
        metadata.put(Metadata.Key.of("x-auth-key", Metadata.ASCII_STRING_MARSHALLER), authKey)
        it.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
    }

    suspend fun getScoreboard(id: String) : GetScoreboardResponse {
        val request = getScoreboardRequest {
            val idInt = id.toIntOrNull()
            if (idInt != null) {
                contestId = idInt
            } else {
                contestUrl = id
            }
        }
        return stub.getScoreboard(request)
    }

    suspend fun getSubmissions(id: String, page: Int, itemsPerPage: Int) : ListContestSubmissionsResponse {
        val request = listContestSubmissionsRequest {
            contestId = contestId {
                val idInt = id.toIntOrNull()
                if (idInt != null) {
                    this.id = idInt
                } else {
                    this.url = id
                }
            }
            paginationParams = paginationRequestParams {
                this.pageIndex = page
                this.itemsPerPage = itemsPerPage
            }
        }
        return stub.listContestSubmissions(request)
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}


internal class CodeDrillsDataSource(val settings: CodeDrillsSettings, creds: Map<String, String>) : FullReloadContestDataSource(5.seconds) {
    val client = CodeDrillsClient(
        settings.url,
        settings.port,
        settings.authKey.get(creds)
    )

    private fun SubmissionVerdict.toInfoVerdict() = when (this) {
        SubmissionVerdict.WAITING -> null
        SubmissionVerdict.COMPILING -> null
        SubmissionVerdict.COMPILED -> null
        SubmissionVerdict.RUNNING -> null
        SubmissionVerdict.COMPILE_ERROR -> Verdict.CompilationError
        SubmissionVerdict.RUNTIME_ERROR -> Verdict.RuntimeError
        SubmissionVerdict.TIME_LIMIT_EXCEEDED -> Verdict.TimeLimitExceeded
        SubmissionVerdict.CORRECT_ANSWER -> Verdict.Accepted
        SubmissionVerdict.WRONG_ANSWER -> Verdict.WrongAnswer
        SubmissionVerdict.SOURCE_LIMIT_EXCEEDED -> Verdict.CompilationError
        SubmissionVerdict.MEMORY_LIMIT_EXCEEDED -> Verdict.MemoryLimitExceeded
        SubmissionVerdict.SKIPPED -> Verdict.Ignored
        SubmissionVerdict.OUTPUT_LIMIT_EXCEEDED -> Verdict.OutputLimitExceeded
        SubmissionVerdict.JUDGE_ERROR -> Verdict.Fail
        SubmissionVerdict.UNRECOGNIZED -> Verdict.Rejected
    }

    override suspend fun loadOnce(): ContestParseResult {
        val scoreboard = client.getScoreboard(settings.contestId)
        val contest = scoreboard.scoreboard.contest
        val problems = scoreboard.scoreboard.problemList.mapIndexed { index, problem ->
            ProblemInfo(
                displayName = problem.index,
                fullName = problem.title,
                id = problem.id,
                contestSystemId = problem.id.toString(),
                ordinal = index
            )
        }
        val teams = scoreboard.scoreboard.rowList.map {
            val team = it.team
            TeamInfo(
                id = team.id,
                contestSystemId = team.id.toString(),
                fullName = team.name,
                displayName = team.name,
                groups = emptyList(),
                hashTag = null,
                medias = emptyMap(),
                organizationId = null,
                isOutOfContest = false,
                isHidden = false
            )
        }

        val memberIdToTeam = scoreboard.scoreboard.rowList
            .map { it.team }
            .flatMap { it.memberList.map { member ->  member.id to it.id } }
            .toMap()

        val startTime = Instant.fromEpochMilliseconds(contest.startTimeMilliSeconds)
        val contestLength = contest.durationSeconds.seconds

        val itemsPerPage = 100
        val submissionsRaw = buildList {
            val page0 = client.getSubmissions(settings.contestId, 0, itemsPerPage)
            addAll(page0.submissionList)
            for (i in itemsPerPage .. page0.paginationParams.total step itemsPerPage) {
                val page = client.getSubmissions(settings.contestId, i / itemsPerPage, itemsPerPage)
                addAll(page.submissionList)
            }
        }
        val submissions = submissionsRaw.mapNotNull {
            val verdict = it.verdict.toInfoVerdict()?.toRunResult()
            val time = Instant.fromEpochMilliseconds(it.submittedOn) - startTime
            if (time >= contestLength) return@mapNotNull null
            if (it.submittedBy !in memberIdToTeam) {
                log.info("Submission by unknown contestant ${it.submittedBy} at ${Instant.fromEpochMilliseconds(it.submittedOn) - startTime}  is ignored")
                return@mapNotNull null
            }
            RunInfo(
                id = it.id,
                result = verdict,
                percentage = if (verdict == null) 0.0 else 1.0,
                problemId = it.problemId,
                teamId = memberIdToTeam[it.submittedBy]!!,
                time = time,
            )
        }


        val contestInfo = ContestInfo(
            name = contest.title,
            status = ContestStatus.byCurrentTime(startTime, contestLength),
            resultType = ContestResultType.ICPC,
            startTime = startTime,
            contestLength = contestLength,
            freezeTime = contestLength - contest.scoreboardSettings.freezeSettings.freezeOffsetTimeInS.seconds,
            problemList = problems,
            teamList = teams,
            penaltyPerWrongAttempt = contest.scoreboardSettings.penaltyPerAttemptInS.seconds,
            groupList = emptyList(),
            penaltyRoundingMode = when (contest.scoreboardSettings.scoreboardType) {
                ScoreboardType.UNKNOWN_SCOREBOARD_TYPE, ScoreboardType.ONE_POINT_WITH_PENALTY -> PenaltyRoundingMode.SUM_DOWN_TO_MINUTE
                ScoreboardType.ONE_POINT_WITH_MAX_PENALTY -> PenaltyRoundingMode.LAST
                ScoreboardType.UNRECOGNIZED, null -> TODO("Unsupported scoreboard type")
            },
            organizationList = emptyList()
        )
        return ContestParseResult(contestInfo, submissions, emptyList())
    }

    companion object {
        val log = getLogger(CodeDrillsDataSource::class)
    }
}
