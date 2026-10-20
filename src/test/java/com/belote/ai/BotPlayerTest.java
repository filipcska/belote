package com.belote.ai;

import com.belote.game.RuleValidator;
import com.belote.model.Card;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BotPlayerTest {

    private final BotPlayer north = new BotPlayer(Seat.NORTH);
    private final CardTracker tracker = new CardTracker();

    private static Card c(Rank rank, Suit suit) { return Card.of(suit, rank); }

    private static Contract contract(ContractType type, Seat declarer) {
        return new Contract(type, declarer, Contract.DoubleState.NONE);
    }

    /** Plays a card into the trick the way the engine does: record first, then add. */
    private void play(ContractType type, Trick trick, Seat seat, Card card) {
        tracker.recordPlay(seat, card, trick, type);
        trick.add(seat, card);
    }

    private Card choose(List<Card> hand, Contract contract, Trick trick) {
        List<Card> legal = RuleValidator.legalMoves(hand, trick, contract.type());
        return north.chooseCard(legal, hand, contract, trick, tracker);
    }

    // --- drawing lead under the partner's contract

    @Test
    void leadsTrumpAceToPullTheNineWhenPartnerCalledTheSuit() {
        List<Card> hand = List.of(c(Rank.ACE, Suit.SPADES), c(Rank.SEVEN, Suit.SPADES),
                c(Rank.ACE, Suit.HEARTS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.SEVEN, Suit.CLUBS));
        Card lead = choose(hand, contract(ContractType.SPADES, Seat.SOUTH), new Trick(Seat.NORTH));
        assertEquals(c(Rank.ACE, Suit.SPADES), lead);
    }

    @Test
    void leadsAceToPullTheNineWhenPartnerCalledAllTrumps() {
        List<Card> hand = List.of(c(Rank.ACE, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS),
                c(Rank.SEVEN, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES), c(Rank.SEVEN, Suit.SPADES));
        Card lead = choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), new Trick(Seat.NORTH));
        assertEquals(c(Rank.ACE, Suit.CLUBS), lead);
    }

    @Test
    void leadsKingToPullTheTenWhenPartnerCalledNoTrumps() {
        List<Card> hand = List.of(c(Rank.KING, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
                c(Rank.NINE, Suit.CLUBS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.SEVEN, Suit.SPADES));
        Card lead = choose(hand, contract(ContractType.NO_TRUMPS, Seat.SOUTH), new Trick(Seat.NORTH));
        assertEquals(c(Rank.KING, Suit.HEARTS), lead);
    }

    @Test
    void doesNotLeadTheTrumpAceIntoAnOpponentsContract() {
        List<Card> hand = List.of(c(Rank.ACE, Suit.SPADES), c(Rank.SEVEN, Suit.SPADES),
                c(Rank.EIGHT, Suit.HEARTS), c(Rank.EIGHT, Suit.DIAMONDS), c(Rank.SEVEN, Suit.CLUBS));
        Card lead = choose(hand, contract(ContractType.SPADES, Seat.EAST), new Trick(Seat.NORTH));
        assertNotEquals(c(Rank.ACE, Suit.SPADES), lead);
    }

    // --- quick fixes

    @Test
    void doesNotDumpTheTrumpJackOnPartnersTrick() {
        Trick trick = new Trick(Seat.WEST);
        play(ContractType.SPADES, trick, Seat.WEST, c(Rank.SEVEN, Suit.HEARTS));
        play(ContractType.SPADES, trick, Seat.SOUTH, c(Rank.ACE, Suit.HEARTS));
        play(ContractType.SPADES, trick, Seat.EAST, c(Rank.EIGHT, Suit.HEARTS));

        List<Card> hand = List.of(c(Rank.JACK, Suit.SPADES), c(Rank.KING, Suit.DIAMONDS),
                c(Rank.SEVEN, Suit.CLUBS));
        assertEquals(c(Rank.KING, Suit.DIAMONDS),
                choose(hand, contract(ContractType.SPADES, Seat.SOUTH), trick));
    }

    @Test
    void doesNotCashAnAceIntoAKnownRuff() {
        // West shows out of hearts while still possibly holding trumps.
        Trick earlier = new Trick(Seat.SOUTH);
        play(ContractType.SPADES, earlier, Seat.SOUTH, c(Rank.KING, Suit.HEARTS));
        play(ContractType.SPADES, earlier, Seat.EAST, c(Rank.SEVEN, Suit.HEARTS));
        play(ContractType.SPADES, earlier, Seat.NORTH, c(Rank.QUEEN, Suit.HEARTS));
        play(ContractType.SPADES, earlier, Seat.WEST, c(Rank.SEVEN, Suit.DIAMONDS));

        List<Card> hand = List.of(c(Rank.ACE, Suit.HEARTS), c(Rank.SEVEN, Suit.CLUBS),
                c(Rank.EIGHT, Suit.DIAMONDS));
        Card lead = choose(hand, contract(ContractType.SPADES, Seat.EAST), new Trick(Seat.NORTH));
        assertNotEquals(c(Rank.ACE, Suit.HEARTS), lead);
    }

    @Test
    void declarerSecondToPlayKeepsBossAceOffACheapTrick() {
        Trick trick = new Trick(Seat.EAST);
        play(ContractType.SPADES, trick, Seat.EAST, c(Rank.NINE, Suit.HEARTS));

        List<Card> hand = List.of(c(Rank.ACE, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS),
                c(Rank.JACK, Suit.SPADES));
        assertEquals(c(Rank.EIGHT, Suit.HEARTS),
                choose(hand, contract(ContractType.SPADES, Seat.NORTH), trick));
    }

    @Test
    void defenderStillTakesACheapTrick() {
        Trick trick = new Trick(Seat.EAST);
        play(ContractType.SPADES, trick, Seat.EAST, c(Rank.NINE, Suit.HEARTS));

        List<Card> hand = List.of(c(Rank.ACE, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS),
                c(Rank.JACK, Suit.SPADES));
        assertEquals(c(Rank.ACE, Suit.HEARTS),
                choose(hand, contract(ContractType.SPADES, Seat.EAST), trick));
    }

    // --- running-suit signal

    private Trick partnerWinningSpadeTrick() {
        Trick trick = new Trick(Seat.WEST);
        play(ContractType.ALL_TRUMPS, trick, Seat.WEST, c(Rank.SEVEN, Suit.SPADES));
        play(ContractType.ALL_TRUMPS, trick, Seat.SOUTH, c(Rank.JACK, Suit.SPADES));
        play(ContractType.ALL_TRUMPS, trick, Seat.EAST, c(Rank.EIGHT, Suit.SPADES));
        return trick;
    }

    @Test
    void dropsTheTopCardOfASuitThatRunsOnPartnersTrick() {
        Trick trick = partnerWinningSpadeTrick();
        List<Card> hand = List.of(c(Rank.NINE, Suit.HEARTS), c(Rank.ACE, Suit.HEARTS),
                c(Rank.JACK, Suit.HEARTS));
        assertEquals(c(Rank.JACK, Suit.HEARTS),
                choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), trick));
    }

    @Test
    void signalsWhenOtherSuitsAreRunningToo() {
        // All other clubs are gone, so the 7 and 8 of clubs win as well.
        for (Rank r : List.of(Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)) {
            tracker.recordPlay(Seat.EAST, c(r, Suit.CLUBS), new Trick(Seat.EAST), ContractType.ALL_TRUMPS);
        }
        Trick trick = partnerWinningSpadeTrick();
        List<Card> hand = List.of(c(Rank.SEVEN, Suit.CLUBS), c(Rank.EIGHT, Suit.CLUBS),
                c(Rank.NINE, Suit.HEARTS), c(Rank.JACK, Suit.HEARTS));
        assertEquals(c(Rank.JACK, Suit.HEARTS),
                choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), trick));
    }

    @Test
    void keepsTheJackWhenTheSuitDoesNotRun() {
        Trick trick = partnerWinningSpadeTrick();
        // The nine of hearts is still out, so the hearts do not all take tricks.
        List<Card> hand = List.of(c(Rank.SEVEN, Suit.HEARTS), c(Rank.ACE, Suit.HEARTS),
                c(Rank.JACK, Suit.HEARTS));
        assertNotEquals(c(Rank.JACK, Suit.HEARTS),
                choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), trick));
    }

    // --- reading the partner's signal

    private Trick spadeTrickWithSouthDroppingTheHeartJack(Card northCard, Card eastCard) {
        ContractType type = ContractType.ALL_TRUMPS;
        Trick trick = new Trick(Seat.NORTH);
        play(type, trick, Seat.NORTH, northCard);
        play(type, trick, Seat.EAST, eastCard);
        play(type, trick, Seat.SOUTH, c(Rank.JACK, Suit.HEARTS));
        play(type, trick, Seat.WEST, c(Rank.EIGHT, Suit.SPADES));
        return trick;
    }

    @Test
    void leadsTheSuitPartnerSignalledBeforeCashingItsOwnWinners() {
        spadeTrickWithSouthDroppingTheHeartJack(c(Rank.JACK, Suit.SPADES), c(Rank.SEVEN, Suit.SPADES));
        List<Card> hand = List.of(c(Rank.SEVEN, Suit.HEARTS), c(Rank.ACE, Suit.DIAMONDS),
                c(Rank.JACK, Suit.CLUBS));
        assertEquals(c(Rank.SEVEN, Suit.HEARTS),
                choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), new Trick(Seat.NORTH)));
    }

    @Test
    void aTopCardDroppedOnAnOpponentsTrickIsNoSignal() {
        spadeTrickWithSouthDroppingTheHeartJack(c(Rank.SEVEN, Suit.SPADES), c(Rank.JACK, Suit.SPADES));
        List<Card> hand = List.of(c(Rank.SEVEN, Suit.HEARTS), c(Rank.ACE, Suit.DIAMONDS),
                c(Rank.JACK, Suit.CLUBS));
        assertNotEquals(c(Rank.SEVEN, Suit.HEARTS),
                choose(hand, contract(ContractType.ALL_TRUMPS, Seat.SOUTH), new Trick(Seat.NORTH)));
    }
}
