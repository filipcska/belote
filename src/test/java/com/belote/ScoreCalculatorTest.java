package com.belote;

import com.belote.game.Announcement;
import com.belote.game.HandResult;
import com.belote.game.ScoreCalculator;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Seat;
import com.belote.model.Suit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreCalculatorTest {

    private static Contract suitContract() {
        return new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.NONE);
    }

    @Test
    void suitContractCardPointsPlusLastTrickTotalOneSixtyTwo() {
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(110, r.rawPoints()[0]);
        assertEquals(52, r.rawPoints()[1]);
        assertEquals(162, r.rawPoints()[0] + r.rawPoints()[1]);
        assertEquals(HandResult.Outcome.MADE, r.outcome());
        assertEquals(11, r.awarded()[0]);
        assertEquals(5, r.awarded()[1]);
    }

    @Test
    void setContractGivesTheDefendersEverything() {
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{40, 112}, new int[]{2, 6}, 1, List.of(), 0);

        assertEquals(HandResult.Outcome.SET, r.outcome());
        assertEquals(0, r.awarded()[0]);
        assertEquals(r.roundedPoints()[0] + r.roundedPoints()[1], r.awarded()[1]);
    }

    @Test
    void equalRawScoresHangTheContract() {
        // 71 + 10 for the last trick against 81 is a dead heat.
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{71, 81}, new int[]{4, 4}, 0, List.of(), 0);

        assertEquals(HandResult.Outcome.HANGING, r.outcome());
        assertEquals(0, r.awarded()[0], "the declarers bank nothing");
        assertTrue(r.awarded()[1] > 0, "the defenders keep their own points");
        assertEquals(r.roundedPoints()[0], r.hangingCreated());
    }

    @Test
    void hangingPointsGoToTheNextHandsWinner() {
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 8);

        assertEquals(HandResult.Outcome.MADE, r.outcome());
        assertEquals(11 + 8, r.awarded()[0]);
        assertEquals(0, r.hangingCreated());
    }

    @Test
    void noTrumpsDoublesEverythingExceptCapot() {
        Contract nt = new Contract(ContractType.NO_TRUMPS, Seat.SOUTH, Contract.DoubleState.NONE);
        HandResult r = ScoreCalculator.score(nt,
                new int[]{80, 40}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(180, r.rawPoints()[0]);   // (80 + 10) * 2
        assertEquals(80, r.rawPoints()[1]);    // 40 * 2
        assertEquals(260, r.rawPoints()[0] + r.rawPoints()[1]);
    }

    @Test
    void capotInASuitContractTotalsTwoHundredFiftyTwo() {
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{152, 0}, new int[]{8, 0}, 0, List.of(), 0);

        assertTrue(r.isCapot());
        assertEquals(0, r.capotTeam());
        assertEquals(252, r.rawPoints()[0]);   // 152 cards + 10 last trick + 90 capot
        assertEquals(0, r.rawPoints()[1]);
    }

    @Test
    void capotBonusIsNotDoubledInNoTrumps() {
        Contract nt = new Contract(ContractType.NO_TRUMPS, Seat.SOUTH, Contract.DoubleState.NONE);
        HandResult r = ScoreCalculator.score(nt,
                new int[]{120, 0}, new int[]{8, 0}, 0, List.of(), 0);

        assertEquals((120 + 10) * 2 + 90, r.rawPoints()[0]);
    }

    @Test
    void doublingMultipliesTheAwardedPoints() {
        Contract doubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.DOUBLED);
        HandResult plain = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);
        HandResult twice = ScoreCalculator.score(doubled,
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(plain.awarded()[0] * 2, twice.awarded()[0]);
    }

    @Test
    void redoublingQuadruplesTheAwardedPoints() {
        Contract redoubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.REDOUBLED);
        HandResult plain = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);
        HandResult four = ScoreCalculator.score(redoubled,
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(plain.awarded()[0] * 4, four.awarded()[0]);
    }

    @Test
    void announcementsCountTowardsTheRawTotal() {
        List<Announcement> resolved = List.of(Announcement.belote(Seat.SOUTH, Suit.HEARTS));
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, resolved, 0);

        assertEquals(130, r.rawPoints()[0]);   // 100 cards + 20 belote + 10 last trick
    }

    @Test
    void halvesRoundAgainstTheContractingTeam() {
        assertEquals(9, ScoreCalculator.roundHalfUp(85));
        assertEquals(8, ScoreCalculator.roundHalfDown(85));
        assertEquals(8, ScoreCalculator.roundHalfUp(84));
        assertEquals(9, ScoreCalculator.roundHalfDown(86));
    }
}
