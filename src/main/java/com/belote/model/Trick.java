package com.belote.model;

import java.util.ArrayList;
import java.util.List;

/** One trick in progress or completed. */
public final class Trick {

    /** A single card laid to the table. */
    public static final class Play {
        private final Seat seat;
        private final Card card;

        public Play(Seat seat, Card card) { this.seat = seat; this.card = card; }

        public Seat seat() { return seat; }
        public Card card() { return card; }
    }

    private final Seat leader;
    private final List<Play> plays = new ArrayList<>(4);

    public Trick(Seat leader) { this.leader = leader; }

    public Seat leader() { return leader; }

    public List<Play> plays() { return plays; }

    public void add(Seat seat, Card card) { plays.add(new Play(seat, card)); }

    public boolean isEmpty() { return plays.isEmpty(); }

    public boolean isComplete() { return plays.size() == 4; }

    public int size() { return plays.size(); }

    /** The suit that was led, or null if nothing has been played yet. */
    public Suit ledSuit() { return plays.isEmpty() ? null : plays.get(0).card().suit(); }

    /** The seat due to play next, assuming the trick is not complete. */
    public Seat nextToPlay() {
        Seat s = leader;
        for (int i = 0; i < plays.size(); i++) s = s.next();
        return s;
    }

    public List<Card> cards() {
        List<Card> out = new ArrayList<>(plays.size());
        for (Play p : plays) out.add(p.card());
        return out;
    }

    public Card cardBy(Seat seat) {
        for (Play p : plays) {
            if (p.seat() == seat) return p.card();
        }
        return null;
    }
}
