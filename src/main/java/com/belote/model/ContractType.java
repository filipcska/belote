package com.belote.model;

/** Contract kinds in ascending bid order. */
public enum ContractType {
    CLUBS(1, Suit.CLUBS, "contract.clubs"),
    DIAMONDS(2, Suit.DIAMONDS, "contract.diamonds"),
    HEARTS(3, Suit.HEARTS, "contract.hearts"),
    SPADES(4, Suit.SPADES, "contract.spades"),
    NO_TRUMPS(5, null, "contract.noTrumps"),
    ALL_TRUMPS(6, null, "contract.allTrumps");

    private final int rank;
    private final Suit trumpSuit;
    private final String key;

    ContractType(int rank, Suit trumpSuit, String key) {
        this.rank = rank;
        this.trumpSuit = trumpSuit;
        this.key = key;
    }

    public int rank() { return rank; }
    public String key() { return key; }

    /** The single trump suit, or null for No Trumps and All Trumps. */
    public Suit trumpSuit() { return trumpSuit; }

    public boolean isSuitContract() { return trumpSuit != null; }

    /** Whether cards of {@code suit} use trump values and trump strength ordering. */
    public boolean isTrumpSuit(Suit suit) {
        if (this == ALL_TRUMPS) return true;
        if (this == NO_TRUMPS) return false;
        return trumpSuit == suit;
    }

    /** True when a card of one suit can beat a card of another (only in suit contracts). */
    public boolean allowsRuffing() { return isSuitContract(); }

    public static ContractType forSuit(Suit suit) {
        for (ContractType t : values()) {
            if (t.trumpSuit == suit) return t;
        }
        throw new IllegalArgumentException("no contract for " + suit);
    }
}
