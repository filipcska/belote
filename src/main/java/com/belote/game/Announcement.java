package com.belote.game;

import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;

/** A meld, carre or belote held by one player. */
public final class Announcement {

    public enum Type {
        TIERCE("ann.tierce", 20),
        QUARTE("ann.quarte", 50),
        QUINTE("ann.quinte", 100),
        CARRE("ann.carre", 0),
        BELOTE("ann.belote", 20);

        private final String key;
        private final int points;

        Type(String key, int points) { this.key = key; this.points = points; }

        public String key() { return key; }
        public int basePoints() { return points; }
        public boolean isSequence() { return this == TIERCE || this == QUARTE || this == QUINTE; }
    }

    private final Type type;
    private final Seat seat;
    private final Suit suit;
    private final Rank topRank;
    private final int length;
    private final int points;

    private Announcement(Type type, Seat seat, Suit suit, Rank topRank, int length, int points) {
        this.type = type;
        this.seat = seat;
        this.suit = suit;
        this.topRank = topRank;
        this.length = length;
        this.points = points;
    }

    public static Announcement sequence(Seat seat, Suit suit, Rank topRank, int length) {
        Type type;
        if (length == 3) type = Type.TIERCE;
        else if (length == 4) type = Type.QUARTE;
        else type = Type.QUINTE;   // 5 or longer scores as a quinte
        return new Announcement(type, seat, suit, topRank, length, type.basePoints());
    }

    public static Announcement carre(Seat seat, Rank rank) {
        return new Announcement(Type.CARRE, seat, null, rank, 4, rank.carreValue());
    }

    public static Announcement belote(Seat seat, Suit suit) {
        return new Announcement(Type.BELOTE, seat, suit, null, 0, Type.BELOTE.basePoints());
    }

    public Type type() { return type; }
    public Seat seat() { return seat; }
    public Suit suit() { return suit; }
    public Rank topRank() { return topRank; }
    public int length() { return length; }
    public int points() { return points; }
    public int team() { return seat.team(); }

    /**
     * Ordering key for sequence priority: longer wins, then the higher top card.
     * Only meaningful between two sequences.
     */
    public int sequenceRank() { return length * 100 + topRank.sequenceOrder(); }

    @Override
    public String toString() {
        return type + (suit != null ? " " + suit.symbol() : "")
                + (topRank != null ? " " + topRank.label() : "") + " (" + points + ")";
    }
}
