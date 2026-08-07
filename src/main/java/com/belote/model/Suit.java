package com.belote.model;

public enum Suit {
    CLUBS("♣", "suit.clubs", true),
    DIAMONDS("♦", "suit.diamonds", false),
    HEARTS("♥", "suit.hearts", false),
    SPADES("♠", "suit.spades", true);

    private final String symbol;
    private final String key;
    private final boolean black;

    Suit(String symbol, String key, boolean black) {
        this.symbol = symbol;
        this.key = key;
        this.black = black;
    }

    public String symbol() { return symbol; }
    public String key() { return key; }
    public boolean isBlack() { return black; }
}
