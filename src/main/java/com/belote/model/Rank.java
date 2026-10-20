package com.belote.model;

/**
 * Ranks in meld-sequence order (7, 8, 9, 10, J, Q, K, A).
 *
 * <p>Three separate orderings matter in Belote and are kept apart deliberately:
 * <ul>
 *   <li>{@link #sequenceOrder()} — adjacency for tierce/quarte/quinte</li>
 *   <li>{@link #trumpStrength()} / {@link #plainStrength()} — who wins a trick</li>
 *   <li>{@link #trumpPoints()} / {@link #plainPoints()} — what the card is worth</li>
 * </ul>
 */
public enum Rank {
    SEVEN("7",  0, 1, 1,  0,  0),
    EIGHT("8",  1, 2, 2,  0,  0),
    NINE("9",   2, 7, 3, 14,  0),
    TEN("10",   3, 5, 7, 10, 10),
    JACK("J",   4, 8, 4, 20,  2),
    QUEEN("Q",  5, 3, 5,  3,  3),
    KING("K",   6, 4, 6,  4,  4),
    ACE("A",    7, 6, 8, 11, 11);

    private final String label;
    private final int sequenceOrder;
    private final int trumpStrength;
    private final int plainStrength;
    private final int trumpPoints;
    private final int plainPoints;

    Rank(String label, int sequenceOrder, int trumpStrength, int plainStrength,
         int trumpPoints, int plainPoints) {
        this.label = label;
        this.sequenceOrder = sequenceOrder;
        this.trumpStrength = trumpStrength;
        this.plainStrength = plainStrength;
        this.trumpPoints = trumpPoints;
        this.plainPoints = plainPoints;
    }

    public String label() { return label; }
    public int sequenceOrder() { return sequenceOrder; }
    public int trumpStrength() { return trumpStrength; }
    public int plainStrength() { return plainStrength; }
    public int trumpPoints() { return trumpPoints; }
    public int plainPoints() { return plainPoints; }

    /** Carre (four of a kind) value; 0 means the rank forms no carre. */
    public int carreValue() {
        switch (this) {
            case JACK: return 200;
            case NINE: return 150;
            case ACE: case KING: case QUEEN: case TEN: return 100;
            default: return 0;
        }
    }
}
