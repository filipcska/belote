package com.belote;

import com.belote.ai.BotPlayer;
import com.belote.game.GameEngine;
import com.belote.game.ScoreCalculator;
import com.belote.model.Card;
import com.belote.model.Seat;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the engine headlessly with bots in every seat. This is the closest
 * thing to an integration test available without a display: if the rules,
 * bidding, announcements or scoring were inconsistent, a full game would throw.
 */
class GameEngineTest {

    private static Map<Seat, BotPlayer> allBots() {
        Map<Seat, BotPlayer> bots = new EnumMap<>(Seat.class);
        for (Seat s : Seat.values()) bots.put(s, new BotPlayer(s));
        return bots;
    }

    /** Plays until someone wins or the step budget runs out. Returns hands played. */
    private static int playGame(GameEngine engine, Map<Seat, BotPlayer> bots, int budget) {
        int hands = 0;
        for (int step = 0; step < budget; step++) {
            switch (engine.phase()) {
                case BIDDING: {
                    Seat s = engine.activeSeat();
                    engine.placeBid(s, bots.get(s).chooseBid(engine));
                    break;
                }
                case PLAYING: {
                    Seat s = engine.activeSeat();
                    engine.playCard(s, bots.get(s).chooseCard(engine));
                    break;
                }
                case HAND_OVER:
                    hands++;
                    engine.nextHand();
                    break;
                case GAME_OVER:
                    return hands + 1;
            }
        }
        return hands;
    }

    @Test
    void everySeatHoldsFiveCardsDuringBidding() {
        GameEngine engine = new GameEngine(new Random(7));
        engine.newGame();

        for (Seat s : Seat.values()) {
            assertEquals(5, engine.player(s).hand().size());
        }
        assertEquals(GameEngine.Phase.BIDDING, engine.phase());
    }

    @Test
    void theDealersRightHandNeighbourOpensTheBidding() {
        GameEngine engine = new GameEngine(new Random(7));
        engine.newGame();

        assertEquals(engine.dealer().next(), engine.activeSeat());
    }

    @Test
    void everySeatHoldsEightDistinctCardsOnceTheContractIsSettled() {
        GameEngine engine = new GameEngine(new Random(11));
        Map<Seat, BotPlayer> bots = allBots();
        engine.newGame();

        int guard = 0;
        while (engine.phase() == GameEngine.Phase.BIDDING && guard++ < 200) {
            Seat s = engine.activeSeat();
            engine.placeBid(s, bots.get(s).chooseBid(engine));
        }

        assertEquals(GameEngine.Phase.PLAYING, engine.phase());

        Set<Card> seen = new HashSet<>();
        for (Seat s : Seat.values()) {
            assertEquals(8, engine.player(s).hand().size(), s + " should hold eight cards");
            seen.addAll(engine.player(s).hand());
        }
        assertEquals(32, seen.size(), "the whole deck must be in play exactly once");
    }

    @Test
    void aFullGameRunsToCompletionWithoutIllegalStates() {
        GameEngine engine = new GameEngine(new Random(2024));
        engine.newGame();

        playGame(engine, allBots(), 20_000);

        assertEquals(GameEngine.Phase.GAME_OVER, engine.phase());
        assertTrue(engine.winningTeam() == 0 || engine.winningTeam() == 1);
        assertTrue(engine.gameScore()[engine.winningTeam()] >= ScoreCalculator.WINNING_THRESHOLD);
    }

    @Test
    void manyGamesWithDifferentDealsAllTerminate() {
        for (int seed = 0; seed < 25; seed++) {
            GameEngine engine = new GameEngine(new Random(seed));
            engine.newGame();
            playGame(engine, allBots(), 20_000);
            assertEquals(GameEngine.Phase.GAME_OVER, engine.phase(), "seed " + seed + " did not finish");
        }
    }

    @Test
    void everyHandDistributesAllThirtyTwoCardsAcrossEightTricks() {
        GameEngine engine = new GameEngine(new Random(99));
        Map<Seat, BotPlayer> bots = allBots();
        engine.newGame();

        int guard = 0;
        while (engine.phase() != GameEngine.Phase.HAND_OVER && guard++ < 2000) {
            Seat s = engine.activeSeat();
            if (engine.phase() == GameEngine.Phase.BIDDING) {
                engine.placeBid(s, bots.get(s).chooseBid(engine));
            } else {
                engine.playCard(s, bots.get(s).chooseCard(engine));
            }
        }

        assertEquals(8, engine.completedTricks().size());
        assertEquals(8, engine.tricksWon()[0] + engine.tricksWon()[1]);
    }
}
