package com.belote.model;

/**
 * Seats in counter-clockwise order, which is also the order of play.
 *
 * <p>On screen South sits at the bottom facing north, so South's right-hand
 * neighbour is East. Dealing, bidding and trick play all advance with
 * {@link #next()}.
 */
public enum Seat {
    SOUTH("seat.south"),
    EAST("seat.east"),
    NORTH("seat.north"),
    WEST("seat.west");

    private final String key;

    Seat(String key) { this.key = key; }

    public String key() { return key; }

    /** The next seat counter-clockwise, i.e. the player to this one's right. */
    public Seat next() { return values()[(ordinal() + 1) % 4]; }

    public Seat partner() { return values()[(ordinal() + 2) % 4]; }

    public boolean isTeammateOf(Seat other) { return this == other || partner() == other; }

    /** Team 0 = South/North, team 1 = East/West. */
    public int team() { return ordinal() % 2; }

    public boolean isHuman() { return this == SOUTH; }
}
