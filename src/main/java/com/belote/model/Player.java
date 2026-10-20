package com.belote.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Player {

    private final Seat seat;
    private final boolean bot;
    private final List<Card> hand = new ArrayList<>(8);

    public Player(Seat seat, boolean bot) {
        this.seat = seat;
        this.bot = bot;
    }

    public Seat seat() { return seat; }
    public boolean isBot() { return bot; }
    public List<Card> hand() { return hand; }

    public void receive(List<Card> cards) { hand.addAll(cards); }

    public void remove(Card card) {
        if (!hand.remove(card)) {
            throw new IllegalArgumentException(seat + " does not hold " + card);
        }
    }

    public void clearHand() { hand.clear(); }

    public boolean hasSuit(Suit suit) {
        for (Card c : hand) {
            if (c.suit() == suit) return true;
        }
        return false;
    }

    public List<Card> cardsOf(Suit suit) {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (c.suit() == suit) out.add(c);
        }
        return out;
    }

    /** Sorts the hand for display: grouped by suit, strongest first for this contract. */
    public void sortForDisplay(ContractType contract) {
        hand.sort(Comparator
                .comparingInt((Card c) -> c.suit().ordinal())
                .thenComparing(Comparator.comparingInt(
                        (Card c) -> c.strength(contract != null && contract.isTrumpSuit(c.suit()))).reversed()));
    }
}
