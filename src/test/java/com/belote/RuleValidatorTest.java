package com.belote;

import com.belote.game.RuleValidator;
import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleValidatorTest {

    private static Card c(Suit s, Rank r) { return Card.of(s, r); }

    @Test
    void anyCardMayLead() {
        List<Card> hand = List.of(c(Suit.CLUBS, Rank.SEVEN), c(Suit.HEARTS, Rank.ACE));
        List<Card> legal = RuleValidator.legalMoves(hand, new Trick(Seat.SOUTH), ContractType.HEARTS);
        assertEquals(2, legal.size());
    }

    @Test
    void mustFollowSuitWhenAble() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.KING));

        List<Card> hand = List.of(
                c(Suit.CLUBS, Rank.SEVEN),
                c(Suit.HEARTS, Rank.ACE),
                c(Suit.SPADES, Rank.TEN));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.HEARTS);
        assertEquals(List.of(c(Suit.CLUBS, Rank.SEVEN)), legal);
    }

    @Test
    void mustRuffWhenVoidAndOpponentsHoldTheTrick() {
        // South leads, East is to play and is void in clubs. South is East's opponent.
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.SEVEN),
                c(Suit.SPADES, Rank.ACE),
                c(Suit.SPADES, Rank.TEN));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.HEARTS);
        assertEquals(List.of(c(Suit.HEARTS, Rank.SEVEN)), legal);
    }

    @Test
    void noObligationToRuffWhenThePartnerIsWinning() {
        // South leads and is still winning; North (South's partner) is void in clubs.
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.SEVEN));

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.SEVEN),
                c(Suit.SPADES, Rank.ACE));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.HEARTS);
        assertEquals(2, legal.size(), "partner is winning, so the trump is optional");
    }

    @Test
    void mustOverTrumpWhenAnOpponentHasAlreadyRuffed() {
        // South leads clubs, East ruffs with the heart 8, North must beat it.
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.EIGHT));

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.SEVEN),   // lower trump, not enough
                c(Suit.HEARTS, Rank.QUEEN),   // higher trump
                c(Suit.SPADES, Rank.ACE));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.HEARTS);
        assertEquals(List.of(c(Suit.HEARTS, Rank.QUEEN)), legal);
    }

    @Test
    void mayPlayAnythingWhenUnableToOverTrump() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.JACK));   // highest trump of all

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.SEVEN),
                c(Suit.SPADES, Rank.ACE));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.HEARTS);
        assertEquals(2, legal.size());
    }

    @Test
    void allTrumpsForcesRaisingWithinTheLedSuit() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.TEN));

        List<Card> hand = List.of(
                c(Suit.CLUBS, Rank.SEVEN),
                c(Suit.CLUBS, Rank.ACE),     // beats the 10 in trump order
                c(Suit.CLUBS, Rank.NINE),    // also beats it
                c(Suit.HEARTS, Rank.ACE));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.ALL_TRUMPS);
        assertEquals(2, legal.size());
        assertTrue(legal.contains(c(Suit.CLUBS, Rank.ACE)));
        assertTrue(legal.contains(c(Suit.CLUBS, Rank.NINE)));
        assertFalse(legal.contains(c(Suit.CLUBS, Rank.SEVEN)));
    }

    @Test
    void allTrumpsAllowsAnyCardOfTheLedSuitWhenNoneIsHigher() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.JACK));   // unbeatable in trump order

        List<Card> hand = List.of(
                c(Suit.CLUBS, Rank.SEVEN),
                c(Suit.CLUBS, Rank.ACE));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.ALL_TRUMPS);
        assertEquals(2, legal.size());
    }

    @Test
    void allTrumpsImposesNothingWhenVoidInTheLedSuit() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.JACK),
                c(Suit.SPADES, Rank.SEVEN));

        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.ALL_TRUMPS);
        assertEquals(2, legal.size());
    }

    @Test
    void noTrumpsNeverForcesAnythingBeyondFollowingSuit() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.TEN));

        List<Card> hand = List.of(
                c(Suit.CLUBS, Rank.SEVEN),
                c(Suit.CLUBS, Rank.ACE));

        // Must follow clubs, but there is no obligation to beat the ten.
        List<Card> legal = RuleValidator.legalMoves(hand, t, ContractType.NO_TRUMPS);
        assertEquals(2, legal.size());
    }

    @Test
    void noTrumpsAllowsFreeDiscardWhenVoid() {
        Trick t = new Trick(Seat.SOUTH);
        t.add(Seat.SOUTH, c(Suit.CLUBS, Rank.ACE));

        List<Card> hand = List.of(
                c(Suit.HEARTS, Rank.ACE),
                c(Suit.SPADES, Rank.SEVEN));

        assertEquals(2, RuleValidator.legalMoves(hand, t, ContractType.NO_TRUMPS).size());
    }
}
