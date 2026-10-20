package com.belote;

import com.belote.game.Announcement;
import com.belote.game.AnnouncementDetector;
import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementTest {

    private static Card c(Suit s, Rank r) { return Card.of(s, r); }

    private static List<Card> hand(Card... cards) { return new ArrayList<>(List.of(cards)); }

    @Test
    void threeInARowIsATierceWorthTwenty() {
        List<Announcement> found = AnnouncementDetector.detectSequences(Seat.SOUTH,
                hand(c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.EIGHT), c(Suit.HEARTS, Rank.NINE)));

        assertEquals(1, found.size());
        assertEquals(Announcement.Type.TIERCE, found.get(0).type());
        assertEquals(20, found.get(0).points());
        assertEquals(Rank.NINE, found.get(0).topRank());
    }

    @Test
    void fourInARowIsAQuarteWorthFifty() {
        List<Announcement> found = AnnouncementDetector.detectSequences(Seat.SOUTH,
                hand(c(Suit.SPADES, Rank.TEN), c(Suit.SPADES, Rank.JACK),
                     c(Suit.SPADES, Rank.QUEEN), c(Suit.SPADES, Rank.KING)));

        assertEquals(1, found.size());
        assertEquals(Announcement.Type.QUARTE, found.get(0).type());
        assertEquals(50, found.get(0).points());
    }

    @Test
    void fiveInARowIsAQuinteWorthOneHundred() {
        List<Announcement> found = AnnouncementDetector.detectSequences(Seat.SOUTH,
                hand(c(Suit.CLUBS, Rank.TEN), c(Suit.CLUBS, Rank.JACK), c(Suit.CLUBS, Rank.QUEEN),
                     c(Suit.CLUBS, Rank.KING), c(Suit.CLUBS, Rank.ACE)));

        assertEquals(1, found.size());
        assertEquals(Announcement.Type.QUINTE, found.get(0).type());
        assertEquals(100, found.get(0).points());
    }

    @Test
    void sequencesUseMeldOrderNotTrickOrder() {
        // J, Q, K is a run in meld order even though the jack outranks them at trick time.
        List<Announcement> found = AnnouncementDetector.detectSequences(Seat.SOUTH,
                hand(c(Suit.HEARTS, Rank.JACK), c(Suit.HEARTS, Rank.QUEEN), c(Suit.HEARTS, Rank.KING)));

        assertEquals(1, found.size());
        assertEquals(Announcement.Type.TIERCE, found.get(0).type());
    }

    @Test
    void gapsBreakASequence() {
        List<Announcement> found = AnnouncementDetector.detectSequences(Seat.SOUTH,
                hand(c(Suit.HEARTS, Rank.SEVEN), c(Suit.HEARTS, Rank.EIGHT), c(Suit.HEARTS, Rank.TEN)));

        assertTrue(found.isEmpty());
    }

    @Test
    void carreValuesFollowTheSpecification() {
        assertEquals(200, AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.JACK)).get(0).points());
        assertEquals(150, AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.NINE)).get(0).points());
        assertEquals(100, AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.ACE)).get(0).points());
        assertEquals(100, AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.TEN)).get(0).points());
    }

    @Test
    void sevensAndEightsFormNoCarre() {
        assertTrue(AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.SEVEN)).isEmpty());
        assertTrue(AnnouncementDetector.detectCarres(Seat.SOUTH, fourOf(Rank.EIGHT)).isEmpty());
    }

    private static List<Card> fourOf(Rank rank) {
        List<Card> out = new ArrayList<>();
        for (Suit s : Suit.values()) out.add(c(s, rank));
        return out;
    }

    @Test
    void beloteNeedsKingAndQueenOfTheTrumpSuit() {
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN),
                            c(Suit.SPADES, Rank.KING), c(Suit.SPADES, Rank.QUEEN));

        assertEquals(1, AnnouncementDetector.detectBelote(Seat.SOUTH, h, ContractType.HEARTS).size());
        // In All Trumps every suit qualifies, so this hand holds two.
        assertEquals(2, AnnouncementDetector.detectBelote(Seat.SOUTH, h, ContractType.ALL_TRUMPS).size());
    }

    @Test
    void noTrumpsAdmitsNoAnnouncementsAtAll() {
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN),
                            c(Suit.HEARTS, Rank.JACK));

        assertTrue(AnnouncementDetector.detect(Seat.SOUTH, h, ContractType.NO_TRUMPS).isEmpty());
    }

    @Test
    void theLongerSequenceSilencesTheOpposingTeam() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.SOUTH, Suit.HEARTS, Rank.KING, 4),   // team 0
                Announcement.sequence(Seat.EAST, Suit.SPADES, Rank.ACE, 3));    // team 1

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(1, resolved.size());
        assertEquals(Seat.SOUTH, resolved.get(0).seat());
        assertEquals(50, AnnouncementDetector.pointsFor(resolved, 0));
        assertEquals(0, AnnouncementDetector.pointsFor(resolved, 1));
    }

    @Test
    void equalLengthIsBrokenByTheTopCard() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.SOUTH, Suit.HEARTS, Rank.QUEEN, 3),
                Announcement.sequence(Seat.EAST, Suit.SPADES, Rank.ACE, 3));

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(1, resolved.size());
        assertEquals(Seat.EAST, resolved.get(0).seat());
    }

    @Test
    void identicalSequencesOnOpposingTeamsCancelEverything() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.SOUTH, Suit.HEARTS, Rank.ACE, 3),
                Announcement.sequence(Seat.NORTH, Suit.CLUBS, Rank.KING, 3),  // same team as South
                Announcement.sequence(Seat.EAST, Suit.SPADES, Rank.ACE, 3));

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertTrue(resolved.isEmpty(), "a tied best sequence wipes out both sides");
    }

    @Test
    void aTeamKeepsAllItsSequencesWhenItHoldsTheBestOne() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.SOUTH, Suit.HEARTS, Rank.ACE, 4),
                Announcement.sequence(Seat.NORTH, Suit.CLUBS, Rank.NINE, 3),
                Announcement.sequence(Seat.EAST, Suit.SPADES, Rank.ACE, 3));

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(2, resolved.size());
        assertEquals(70, AnnouncementDetector.pointsFor(resolved, 0));
    }

    @Test
    void carresAndSequencesDoNotCancelEachOther() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.SOUTH, Suit.HEARTS, Rank.NINE, 3),   // team 0
                Announcement.carre(Seat.EAST, Rank.JACK));                      // team 1

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(2, resolved.size());
        assertEquals(20, AnnouncementDetector.pointsFor(resolved, 0));
        assertEquals(200, AnnouncementDetector.pointsFor(resolved, 1));
    }

    @Test
    void beloteIsNeverCancelled() {
        List<Announcement> all = List.of(
                Announcement.sequence(Seat.EAST, Suit.SPADES, Rank.ACE, 5),
                Announcement.belote(Seat.SOUTH, Suit.HEARTS));

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(20, AnnouncementDetector.pointsFor(resolved, 0));
        assertEquals(100, AnnouncementDetector.pointsFor(resolved, 1));
    }

    @Test
    void carresAreRankedByTrumpValue() {
        // Both A and K carres are worth 100, but the aces outrank the kings.
        List<Announcement> all = List.of(
                Announcement.carre(Seat.SOUTH, Rank.KING),
                Announcement.carre(Seat.EAST, Rank.ACE));

        List<Announcement> resolved = AnnouncementDetector.resolve(all);

        assertEquals(1, resolved.size());
        assertEquals(Seat.EAST, resolved.get(0).seat());
    }

    @Test
    void aCardSharedByACarreAndASequenceCountsOnlyOnce() {
        // Four queens plus J-Q-K of hearts: the heart queen cannot serve both.
        List<Card> h = hand(c(Suit.HEARTS, Rank.QUEEN), c(Suit.CLUBS, Rank.QUEEN),
                            c(Suit.DIAMONDS, Rank.QUEEN), c(Suit.SPADES, Rank.QUEEN),
                            c(Suit.HEARTS, Rank.JACK), c(Suit.HEARTS, Rank.KING),
                            c(Suit.CLUBS, Rank.SEVEN), c(Suit.DIAMONDS, Rank.EIGHT));

        List<Announcement> found = AnnouncementDetector.detect(Seat.SOUTH, h, ContractType.SPADES);

        assertEquals(1, found.size(), "the carre (100) beats the tierce (20)");
        assertEquals(Announcement.Type.CARRE, found.get(0).type());
    }

    @Test
    void cardsLeftOverByACarreMayStillFormASequence() {
        // Four kings plus 9-10-J-Q-K of hearts: the carre takes the heart king,
        // which leaves 9-10-J-Q as a quarte.
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.CLUBS, Rank.KING),
                            c(Suit.DIAMONDS, Rank.KING), c(Suit.SPADES, Rank.KING),
                            c(Suit.HEARTS, Rank.NINE), c(Suit.HEARTS, Rank.TEN),
                            c(Suit.HEARTS, Rank.JACK), c(Suit.HEARTS, Rank.QUEEN));

        List<Announcement> found = AnnouncementDetector.detect(Seat.SOUTH, h, ContractType.SPADES);

        assertEquals(150, found.stream().mapToInt(Announcement::points).sum());
    }

    @Test
    void beloteIsDeclaredWithTheFirstOfKingAndQueenWhenLeading() {
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN));
        Announcement a = AnnouncementDetector.beloteOnPlay(Seat.SOUTH, c(Suit.HEARTS, Rank.QUEEN), h,
                new Trick(Seat.SOUTH), ContractType.HEARTS);

        assertEquals(Announcement.Type.BELOTE, a.type());
    }

    @Test
    void theSecondCardOfTheBeloteEarnsNothingMore() {
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING));   // the queen is already gone
        assertNull(AnnouncementDetector.beloteOnPlay(Seat.SOUTH, c(Suit.HEARTS, Rank.KING), h,
                new Trick(Seat.SOUTH), ContractType.HEARTS));
    }

    @Test
    void beloteCountsWhenRuffingInASuitContract() {
        Trick t = new Trick(Seat.EAST);
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.ACE));
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN));

        assertEquals(Announcement.Type.BELOTE, AnnouncementDetector.beloteOnPlay(
                Seat.SOUTH, c(Suit.HEARTS, Rank.KING), h, t, ContractType.HEARTS).type());
    }

    @Test
    void allTrumpsBeloteIsLostWhenTheKingOrQueenIsDiscardedOnAnotherSuit() {
        Trick t = new Trick(Seat.EAST);
        t.add(Seat.EAST, c(Suit.CLUBS, Rank.ACE));
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN));

        assertNull(AnnouncementDetector.beloteOnPlay(
                Seat.SOUTH, c(Suit.HEARTS, Rank.KING), h, t, ContractType.ALL_TRUMPS));

        t = new Trick(Seat.EAST);
        t.add(Seat.EAST, c(Suit.HEARTS, Rank.SEVEN));
        assertEquals(Announcement.Type.BELOTE, AnnouncementDetector.beloteOnPlay(
                Seat.SOUTH, c(Suit.HEARTS, Rank.KING), h, t, ContractType.ALL_TRUMPS).type(),
                "following the led suit keeps it");
    }

    @Test
    void noBeloteInNoTrumps() {
        List<Card> h = hand(c(Suit.HEARTS, Rank.KING), c(Suit.HEARTS, Rank.QUEEN));
        assertNull(AnnouncementDetector.beloteOnPlay(Seat.SOUTH, c(Suit.HEARTS, Rank.KING), h,
                new Trick(Seat.SOUTH), ContractType.NO_TRUMPS));
    }
}
