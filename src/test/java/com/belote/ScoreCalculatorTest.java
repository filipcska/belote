package com.belote;

import com.belote.game.Announcement;
import com.belote.game.HandResult;
import com.belote.game.ScoreCalculator;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void underADoubleTheWinnerTakesTheWholeDoubledHand() {
        Contract doubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.DOUBLED);
        HandResult r = ScoreCalculator.score(doubled,
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(HandResult.Outcome.MADE, r.outcome());
        assertEquals((11 + 5) * 2, r.awarded()[0]);
        assertEquals(0, r.awarded()[1], "the losing side writes nothing under a double");
    }

    @Test
    void underADoubleTheDefendersTakeEverythingWhenTheyWin() {
        Contract doubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.DOUBLED);
        HandResult r = ScoreCalculator.score(doubled,
                new int[]{40, 112}, new int[]{2, 6}, 1, List.of(), 0);

        assertEquals(0, r.awarded()[0]);
        assertEquals(16 * 2, r.awarded()[1]);
    }

    @Test
    void redoublingQuadruplesTheWholeHandForTheWinner() {
        Contract redoubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.REDOUBLED);
        HandResult r = ScoreCalculator.score(redoubled,
                new int[]{100, 52}, new int[]{5, 3}, 0, List.of(), 0);

        assertEquals(16 * 4, r.awarded()[0]);
        assertEquals(0, r.awarded()[1]);
    }

    @Test
    void aHangingDoubledHandFreezesAllOfItsPoints() {
        Contract doubled = new Contract(ContractType.HEARTS, Seat.SOUTH, Contract.DoubleState.DOUBLED);
        HandResult r = ScoreCalculator.score(doubled,
                new int[]{71, 81}, new int[]{4, 4}, 0, List.of(), 5);

        assertEquals(HandResult.Outcome.HANGING, r.outcome());
        assertEquals(0, r.awarded()[0]);
        assertEquals(0, r.awarded()[1]);
        assertEquals(16 * 2 + 5, r.hangingCreated());
    }

    @Test
    void announcementsCountTowardsTheRawTotal() {
        List<Announcement> resolved = List.of(Announcement.belote(Seat.SOUTH, Suit.HEARTS));
        HandResult r = ScoreCalculator.score(suitContract(),
                new int[]{100, 52}, new int[]{5, 3}, 0, resolved, 0);

        assertEquals(130, r.rawPoints()[0]);   // 100 cards + 20 belote + 10 last trick
    }

    @Test
    void roundedSuitScoresAlwaysAddUpToSixteen() {
        for (int declarers = 0; declarers <= 162; declarers++) {
            int[] r = ScoreCalculator.round(new int[]{declarers, 162 - declarers}, 0);
            assertEquals(16, r[0] + r[1], "split " + declarers + "/" + (162 - declarers));
        }
    }

    @Test
    void roundedAllTrumpsAndNoTrumpsScoresAlwaysAddUpToTwentySix() {
        for (int a = 0; a <= 258; a++) {
            int[] r = ScoreCalculator.round(new int[]{a, 258 - a}, 0);
            assertEquals(26, r[0] + r[1], "All Trumps split " + a + "/" + (258 - a));
        }
        for (int a = 0; a <= 260; a += 2) {   // No Trumps points are doubled, so always even
            int[] r = ScoreCalculator.round(new int[]{a, 260 - a}, 0);
            assertEquals(26, r[0] + r[1], "No Trumps split " + a + "/" + (260 - a));
        }
    }

    @Test
    void theLargerRemainderRoundsUp() {
        assertArrayEquals(new int[]{8, 8}, ScoreCalculator.round(new int[]{85, 77}, 0));
        assertArrayEquals(new int[]{8, 8}, ScoreCalculator.round(new int[]{77, 85}, 0));
        assertArrayEquals(new int[]{12, 14}, ScoreCalculator.round(new int[]{123, 135}, 0));
    }

    @Test
    void allTrumpsScoresEndingInFourRoundUpForTheTeamWithFewerPoints() {
        assertArrayEquals(new int[]{13, 13}, ScoreCalculator.round(new int[]{124, 134}, 0));
        assertArrayEquals(new int[]{13, 13}, ScoreCalculator.round(new int[]{134, 124}, 1));
    }

    @Test
    void deadHeatsRoundTheHangingPointsUp() {
        // The examples from the rules: 106-106 in a suit, 154-154 in All Trumps.
        Contract suit = suitContract();
        HandResult r = ScoreCalculator.score(suit, new int[]{46, 106}, new int[]{4, 4}, 0,
                List.of(Announcement.sequence(Seat.SOUTH, Suit.SPADES, Rank.KING, 4)), 0);
        assertEquals(HandResult.Outcome.HANGING, r.outcome());
        assertEquals(10, r.awarded()[1]);
        assertEquals(11, r.hangingCreated());

        Contract allTrumps = new Contract(ContractType.ALL_TRUMPS, Seat.SOUTH, Contract.DoubleState.NONE);
        r = ScoreCalculator.score(allTrumps, new int[]{94, 154}, new int[]{4, 4}, 0,
                List.of(Announcement.sequence(Seat.SOUTH, Suit.SPADES, Rank.KING, 4)), 0);
        assertEquals(HandResult.Outcome.HANGING, r.outcome());
        assertEquals(15, r.awarded()[1]);
        assertEquals(16, r.hangingCreated());
    }
}
