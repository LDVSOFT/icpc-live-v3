import _ from "lodash";
import PropTypes from "prop-types";
import React, { useEffect, useState } from "react";
import { useSelector } from "react-redux";
import styled from "styled-components";
import {
    SCOREBOARD_MAX_PAGES,
    SCOREBOARD_NAME_WIDTH,
    SCOREBOARD_RANK_WIDTH,
    SCOREBOARD_ROW_TRANSITION_TIME,
    SCOREBOARD_SCROLL_INTERVAL,
    SCOREBOARD_SUM_PEN_WIDTH,
    SCOREBOARD_TEAMS_ON_PAGE,
    VERDICT_NOK,
    VERDICT_OK,
    VERDICT_UNKNOWN
} from "../../../config";
import { SCOREBOARD_TYPES } from "../../../consts";
import { Cell } from "../../atoms/Cell";
import { ProblemCell, RankCell, TeamNameCell } from "../../atoms/ContestCells";
import { StarIcon } from "../../atoms/Star";


const ScoreboardWrap = styled.div`
  height: 100%;
  width: 100%;
  opacity: 0.8;
  border: none;
  border-collapse: collapse;
  table-layout: fixed;
  display: flex;
  flex-direction: column;
`;

const ScoreboardRowContainer = styled.div`
  height: 100%;
  width: 100%;
  display: flex;
`;

const ScoreboardCell = styled(Cell)`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0;
  position: relative;
`;

const ScoreboardStatCell = styled(ScoreboardCell)`
  width: ${SCOREBOARD_SUM_PEN_WIDTH}px;
`;

const ScoreboardTaskCellWrap = styled(ScoreboardCell)`
  flex-grow: 1;
  flex-shrink: 1;
  flex-basis: 100%;
`;

const TeamTaskStatus = Object.freeze({
    solved: 1,
    failed: 2,
    untouched: 3,
    unknown: 4,
    first: 5
});

const TeamTaskSymbol = Object.freeze({
    [TeamTaskStatus.solved]: "+",
    [TeamTaskStatus.failed]: "-",
    [TeamTaskStatus.untouched]: "",
    [TeamTaskStatus.unknown]: "?",
    [TeamTaskStatus.first]: "+",
});

const TeamTaskColor = Object.freeze({
    [TeamTaskStatus.solved]: VERDICT_OK,
    [TeamTaskStatus.failed]: VERDICT_NOK,
    [TeamTaskStatus.untouched]: undefined,
    [TeamTaskStatus.unknown]: VERDICT_UNKNOWN,
    [TeamTaskStatus.first]: VERDICT_OK,
});

const ScoreboardTaskCell = ({ status, attempts }) => {
    return <ScoreboardTaskCellWrap background={TeamTaskColor[status]}>
        {status === TeamTaskStatus.first && <StarIcon/>}
        {TeamTaskSymbol[status]}
        {status !== TeamTaskStatus.untouched && attempts > 0 && attempts}
    </ScoreboardTaskCellWrap>;
};

ScoreboardTaskCell.propTypes = {
    status: PropTypes.oneOf(Object.values(TeamTaskStatus)),
    attempts: PropTypes.number
};

const ScoreboardHeaderWrap = styled(ScoreboardRowContainer)`
  height: ${props => props.rowHeight}px;
`;

const ScoreboardHeaderTitle = styled(ScoreboardCell)`
  background: red;
  width: ${SCOREBOARD_RANK_WIDTH + SCOREBOARD_NAME_WIDTH}px;
  font-size: 30px;
`;

const ScoreboardHeaderStatCell = styled(ScoreboardStatCell)`
  background: black;
  width: ${SCOREBOARD_SUM_PEN_WIDTH}px;
  text-align: center;
`;

const ScoreboardHeaderProblemCell = styled(ProblemCell)`
  position: relative;
`;

function getStatus(isFirstToSolve, isSolved, pendingAttempts, wrongAttempts) {
    if (isFirstToSolve) {
        return TeamTaskStatus.first;
    } else if (isSolved) {
        return TeamTaskStatus.solved;
    } else if (pendingAttempts > 0) {
        return TeamTaskStatus.unknown;
    } else if (wrongAttempts > 0) {
        return TeamTaskStatus.failed;
    } else {
        return TeamTaskStatus.untouched;
    }
}

const ScoreboardRow = ({ teamId }) => {
    const scoreboardData = useSelector((state) => state.scoreboard[SCOREBOARD_TYPES.normal].ids[teamId]);
    const teamData = useSelector((state) => state.contestInfo.info?.teamsId[teamId]);
    return <ScoreboardRowContainer>
        <RankCell rank={scoreboardData.rank} width={SCOREBOARD_RANK_WIDTH + "px"}/>
        <TeamNameCell teamName={teamData.shortName} width={SCOREBOARD_NAME_WIDTH + "px"} canGrow={false} canShrink={false}/>
        <ScoreboardStatCell>
            {scoreboardData.totalScore}
        </ScoreboardStatCell>
        <ScoreboardStatCell>
            {scoreboardData.penalty}
        </ScoreboardStatCell>
        {scoreboardData.problemResults.map(({ wrongAttempts, pendingAttempts, isSolved, isFirstToSolve }, i) =>
            <ScoreboardTaskCell key={i} status={getStatus(isFirstToSolve, isSolved, pendingAttempts, wrongAttempts)}
                attempts={wrongAttempts + pendingAttempts}/>
        )}
    </ScoreboardRowContainer>;
};
ScoreboardRow.propTypes = {
    teamId: PropTypes.number.isRequired
};

const ScoreboardHeader = ({ problems, rowHeight }) => {
    return <ScoreboardHeaderWrap rowHeight={rowHeight}>
        <ScoreboardHeaderTitle>CURRENT STANDINGS</ScoreboardHeaderTitle>
        <ScoreboardHeaderStatCell>&#931;</ScoreboardHeaderStatCell>
        <ScoreboardHeaderStatCell>PEN</ScoreboardHeaderStatCell>
        {problems && problems.map((probData) =>
            <ScoreboardHeaderProblemCell key={probData.name} probData={probData} canGrow={true} canShrink={true}
                basis={"100%"}/>
        )}
    </ScoreboardHeaderWrap>;
};

ScoreboardHeader.propTypes = {
    problems: PropTypes.arrayOf(PropTypes.object)
};

const ScoreboardRowWrap = styled.div.attrs((props) => ({
    style: {
        top: props.pos + "px"
    }
}))`
  left: 0;
  right: 0;
  height: ${props => props.rowHeight + 2}px; // FIXME lol
  transition: top ${SCOREBOARD_ROW_TRANSITION_TIME}ms ease-out;
  position: absolute;
`;
/**
 * Aligned vertically with zIndex
 * @type {StyledComponent<"div", AnyIfEmpty<DefaultTheme>, function({zIndex: *}): {style: {zIndex: *}}, keyof function({zIndex: *}): {style: {zIndex: *}}>}
 */
const PositionedScoreboardRowWrap = styled.div.attrs(({ zIndex }) => ({
    style: {
        zIndex: zIndex
    }
}
))`
  position: relative;
`;

const PositionedScoreboardRow = ({ zIndex, children, ...rest }) => {
    return <PositionedScoreboardRowWrap zIndex={zIndex}>
        <ScoreboardRowWrap {...rest}>
            {children}
        </ScoreboardRowWrap>
    </PositionedScoreboardRowWrap>;
};

PositionedScoreboardRow.propTypes = {
    zIndex: PropTypes.number,
    children: PropTypes.node
};

const SCOREBOARD_TYPE = SCOREBOARD_TYPES.normal;

export const Scoreboard = ({ widgetData }) => {
    let rows = useSelector((state) => state.scoreboard[SCOREBOARD_TYPE].rows);
    const contestInfo = useSelector((state) => state.contestInfo.info);
    const [offset, setOffset] = useState(0);
    const totalHeight = widgetData.location.sizeY;
    const rowHeight = (totalHeight / (SCOREBOARD_TEAMS_ON_PAGE));
    useEffect(() => {
        const id = setInterval(() => {
            setOffset((offset) => {
                let newStart = offset + SCOREBOARD_TEAMS_ON_PAGE;
                return (newStart >= Math.min(rows.length, SCOREBOARD_MAX_PAGES * SCOREBOARD_TEAMS_ON_PAGE) ? 0 : newStart);
            });
        }, SCOREBOARD_SCROLL_INTERVAL);
        return () => clearInterval(id);
    }, [rows.length]);
    const teams = _(rows).toPairs().sortBy("[1].teamId").value();
    return <ScoreboardWrap>
        <ScoreboardHeader problems={contestInfo?.problems} rowHeight={rowHeight} key={"header"}/>
        <div style={{ overflow: "hidden", height: "100%" }}>
            {teams.map(([ind, teamRowData]) =>
                <PositionedScoreboardRow key={teamRowData.teamId} pos={(ind - offset) * rowHeight}
                    rowHeight={rowHeight} zIndex={-ind}>
                    <ScoreboardRow teamId={teamRowData.teamId}/>
                </PositionedScoreboardRow>
            )}
        </div>
    </ScoreboardWrap>;
};

Scoreboard.propTypes = {
    widgetData: PropTypes.object.isRequired
};

export default Scoreboard;
