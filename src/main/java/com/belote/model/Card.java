package com.belote.model;

import java.util.Objects;

public final class Card {
    private final Suit suit;
    private final Rank rank;

    public Card(Suit suit, Rank rank) {
        this.suit = Objects.requireNonNull(suit);
        this.rank = Objects.requireNonNull(rank);
    }

    public static Card of(Suit suit, Rank rank) { return new Card(suit, rank); }

    public Suit suit() { return suit; }
    public Rank rank() { return rank; }

    public int points(boolean asTrump) {
        return asTrump ? rank.trumpPoints() : rank.plainPoints();
    }

    public int strength(boolean asTrump) {
        return asTrump ? rank.trumpStrength() : rank.plainStrength();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card)) return false;
        Card c = (Card) o;
        return suit == c.suit && rank == c.rank;
    }

    @Override
    public int hashCode() { return suit.ordinal() * 8 + rank.ordinal(); }

    @Override
    public String toString() { return rank.label() + suit.symbol(); }
}
