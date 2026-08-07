package com.belote;

import com.belote.game.TrickResolver;
import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrickResolverTest {

    private static Card c(Suit s, Rank r) { return Card.of(s, r); }

    @Test
    void highestOfLedSuitWinsWhenNobodyRuffs() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.KING));
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.SEVEN));
        t.add(Seat.WEST, c(Suit.CLUBS, Rank.TEN));

        assertEquals(Seat.EAST, TrickResolver.winner(t, ContractType.HEARTS));
    }

    @Test
    void trumpBeatsAnyPlainCardInASuitContract() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.SEVEN));   // ruff
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.TEN));
        t.add(Seat.WEST, c(Suit.CLUBS, Rank.KING));

        assertEquals(Seat.EAST, TrickResolver.winner(t, ContractType.HEARTS));
    }

    @Test
    void higherTrumpBeatsLowerTrump() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.NINE));
        t.add(Seat.NORTH, c(Suit.HEARTS, Rank.JACK));   // J outranks 9 in trump
        t.add(Seat.WEST, c(Suit.HEARTS, Rank.ACE));

        assertEquals(Seat.NORTH, TrickResolver.winner(t, ContractType.HEARTS));
    }

    @Test
    void allTrumpsHasNoCrossSuitRuff() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.SPADES, Rank.JACK));    // worth 20 but cannot win
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.SEVEN));
        t.add(Seat.WEST, c(Suit.HEARTS, Rank.NINE));

        assertEquals(Seat.SOUTH, TrickResolver.winner(t, ContractType.ALL_TRUMPS));
    }

    @Test
    void allTrumpsRanksJackAboveNineAboveAce() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.NINE));
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.JACK));
        t.add(Seat.WEST, c(Suit.CLUBS, Rank.TEN));

        assertEquals(Seat.NORTH, TrickResolver.winner(t, ContractType.ALL_TRUMPS));
    }

    @Test
    void noTrumpsRanksAceAboveTenAboveKing() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.JACK));
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.TEN));
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.WEST, c(Suit.CLUBS, Rank.KING));

        assertEquals(Seat.NORTH, TrickResolver.winner(t, ContractType.NO_TRUMPS));
    }

    @Test
    void suitContractCardValuesMatchTheSpecification() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.HEARTS, Rank.JACK));   // trump jack  = 20
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.NINE));    // trump nine  = 14
        t.add(Seat.NORTH, c(Suit.CLUBS, Rank.JACK));    // plain jack  =  2
        t.add(Seat.WEST, c(Suit.CLUBS, Rank.NINE));     // plain nine  =  0

        assertEquals(36, TrickResolver.points(t, ContractType.HEARTS));
    }

    @Test
    void allTrumpsValuesEveryJackAtTwenty() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.HEARTS, Rank.JACK));
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.JACK));
        t.add(Seat.NORTH, c(Suit.SPADES, Rank.JACK));
        t.add(Seat.WEST, c(Suit.DIAMONDS, Rank.JACK));

        assertEquals(80, TrickResolver.points(t, ContractType.ALL_TRUMPS));
    }
}
