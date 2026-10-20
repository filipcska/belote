package com.belote.ai;

import com.belote.game.TrickResolver;
import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Deck;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers every card played and what it implies, so bots can reason about
 * what is still outstanding rather than guessing.
 */
public final class CardTracker {

    private final Set<Card> played = new HashSet<>();
    private final Map<Seat, Set<Suit>> knownVoids = new EnumMap<>(Seat.class);
    private final Map<Seat, Suit> lastLed = new EnumMap<>(Seat.class);
    private final Map<Seat, Suit> signalled = new EnumMap<>(Seat.class);

    public CardTracker() { reset(); }

    public void reset() {
        played.clear();
        knownVoids.clear();
        lastLed.clear();
        signalled.clear();
        for (Seat s : Seat.values()) {
            knownVoids.put(s, EnumSet.noneOf(Suit.class));
        }
    }

    /** Records a card before it is added to {@code trick}. */
    public void recordPlay(Seat seat, Card card, Trick trick, ContractType contract) {
        if (trick.isEmpty()) {
            lastLed.put(seat, card.suit());
        } else {
            Suit led = trick.ledSuit();
            if (card.suit() != led) {
                // Failing to follow suit proves the seat is void in it.
                knownVoids.get(seat).add(led);
                // Dropping the top remaining card of a suit on the partner's trick
                // asks the partner to lead that suit next.
                Seat winning = TrickResolver.winner(trick, contract);
                if (winning == seat.partner() && isBoss(card, contract, List.of())) {
                    signalled.put(seat, card.suit());
                }
            }
        }
        played.add(card);
    }

    public boolean hasBeenPlayed(Card card) { return played.contains(card); }

    public int playedCount() { return played.size(); }

    public boolean isKnownVoid(Seat seat, Suit suit) { return knownVoids.get(seat).contains(suit); }

    /** The suit a seat last asked its partner to lead by dropping its top card, or null. */
    public Suit signalledBy(Seat seat) { return signalled.get(seat); }

    /** The suit a seat led most recently, or null. */
    public Suit lastLedBy(Seat seat) { return lastLed.get(seat); }

    /** True when no unseen card can beat this one in its own suit. */
    public boolean isBoss(Card card, ContractType contract, List<Card> ownHand) {
        return unseenAbove(card, contract, ownHand) == 0;
    }

    /**
     * How many unseen cards outrank this one in its own suit. Cards in
     * {@code ownHand} are known to us, so they do not count.
     */
    public int unseenAbove(Card card, ContractType contract, List<Card> ownHand) {
        boolean asTrump = contract.isTrumpSuit(card.suit());
        int mine = card.strength(asTrump);
        int count = 0;
        for (Card other : Deck.fullDeck()) {
            if (other.suit() != card.suit()) continue;
            if (other.equals(card)) continue;
            if (played.contains(other)) continue;
            if (ownHand.contains(other)) continue;
            if (other.strength(asTrump) > mine) count++;
        }
        return count;
    }

    /** Every card of {@code ownHand} that is currently unbeatable in its suit. */
    public List<Card> bossCards(ContractType contract, List<Card> ownHand) {
        List<Card> out = new ArrayList<>();
        for (Card c : ownHand) {
            if (isBoss(c, contract, ownHand)) out.add(c);
        }
        return out;
    }

    /** How many cards of a suit are still unaccounted for outside our own hand. */
    public int outstanding(Suit suit, List<Card> ownHand) {
        int count = 0;
        for (Card c : Deck.fullDeck()) {
            if (c.suit() != suit) continue;
            if (played.contains(c) || ownHand.contains(c)) continue;
            count++;
        }
        return count;
    }
}
