package com.belote.game;

import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

/** Decides who is winning a trick and what it is worth. */
public final class TrickResolver {

    private TrickResolver() {}

    /**
     * The seat currently winning the trick. Works on partial tricks too, which is
     * what the play constraints need.
     */
    public static Seat winner(Trick trick, ContractType contract) {
        if (trick.isEmpty()) return null;

        Suit led = trick.ledSuit();
        Seat best = null;
        int bestScore = -1;

        for (Trick.Play play : trick.plays()) {
            Card card = play.card();
            boolean isTrump = contract.isTrumpSuit(card.suit());

            // Only trumps and cards of the led suit can win. Ruffing with a different
            // suit is possible only in a suit contract; in All Trumps every suit is a
            // "trump" for value and ordering, but a foreign suit still cannot win.
            boolean ruffing = contract.allowsRuffing() && isTrump && card.suit() != led;
            if (card.suit() != led && !ruffing) continue;

            // A ruff outranks anything in the led suit.
            int score = card.strength(isTrump) + (ruffing ? 100 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = play.seat();
            }
        }
        return best;
    }

    /** Total card points in the trick, using the contract's value table. */
    public static int points(Trick trick, ContractType contract) {
        int total = 0;
        for (Trick.Play play : trick.plays()) {
            total += play.card().points(contract.isTrumpSuit(play.card().suit()));
        }
        return total;
    }

    /** True when at least one card of a trump suit has been played into the trick. */
    public static boolean containsTrump(Trick trick, ContractType contract) {
        if (!contract.allowsRuffing()) return false;
        for (Trick.Play play : trick.plays()) {
            if (contract.isTrumpSuit(play.card().suit())) return true;
        }
        return false;
    }

    /** Strength of the highest trump in the trick, or -1 if there is none. */
    public static int highestTrumpStrength(Trick trick, ContractType contract) {
        int best = -1;
        for (Trick.Play play : trick.plays()) {
            Card c = play.card();
            if (contract.isTrumpSuit(c.suit())) {
                best = Math.max(best, c.strength(true));
            }
        }
        return best;
    }

    /** Strength of the highest card of the led suit, or -1 if the trick is empty. */
    public static int highestOfLedSuit(Trick trick, ContractType contract) {
        Suit led = trick.ledSuit();
        if (led == null) return -1;
        boolean asTrump = contract.isTrumpSuit(led);
        int best = -1;
        for (Trick.Play play : trick.plays()) {
            Card c = play.card();
            if (c.suit() == led) best = Math.max(best, c.strength(asTrump));
        }
        return best;
    }
}
