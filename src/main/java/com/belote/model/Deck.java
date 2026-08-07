package com.belote.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The 32-card Belote deck: a bridge deck with 2 through 6 removed. */
public final class Deck {

    private final List<Card> cards = new ArrayList<>(32);
    private int dealt;

    public Deck() { reset(); }

    public void reset() {
        cards.clear();
        for (Suit s : Suit.values()) {
            for (Rank r : Rank.values()) {
                cards.add(new Card(s, r));
            }
        }
        dealt = 0;
    }

    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
        dealt = 0;
    }

    public List<Card> deal(int count) {
        if (dealt + count > cards.size()) {
            throw new IllegalStateException("not enough cards left: " + (cards.size() - dealt));
        }
        List<Card> out = new ArrayList<>(cards.subList(dealt, dealt + count));
        dealt += count;
        return out;
    }

    public int remaining() { return cards.size() - dealt; }

    public static List<Card> fullDeck() {
        return new Deck().cards;
    }
}
