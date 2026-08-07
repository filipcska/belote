package com.belote.game;

import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import java.util.ArrayList;
import java.util.List;

/**
 * The single source of truth for which cards may legally be played.
 *
 * <p>Rule interpretation note: the specification states the mandatory-raise
 * (качване) rule only for All Trumps, and states the trump / over-trump
 * obligations for suit contracts only in the "cannot follow suit" branch.
 * This implementation follows that wording literally, so in a suit contract a
 * player who follows a trump lead may play any trump, high or low. See README.
 */
public final class RuleValidator {

    private RuleValidator() {}

    public static List<Card> legalMoves(List<Card> hand, Trick trick, ContractType contract) {
        if (trick == null || trick.isEmpty()) {
            return new ArrayList<>(hand);
        }

        Suit led = trick.ledSuit();
        List<Card> ofLedSuit = filterSuit(hand, led);

        if (!ofLedSuit.isEmpty()) {
            return followingSuit(ofLedSuit, trick, contract, led);
        }
        return cannotFollowSuit(hand, trick, contract);
    }

    public static boolean isLegal(Card card, List<Card> hand, Trick trick, ContractType contract) {
        return legalMoves(hand, trick, contract).contains(card);
    }

    /** The player holds the led suit and must play it; All Trumps may force a raise. */
    private static List<Card> followingSuit(List<Card> ofLedSuit, Trick trick,
                                            ContractType contract, Suit led) {
        if (contract != ContractType.ALL_TRUMPS) {
            return ofLedSuit;
        }
        // All Trumps: must overtake within the led suit when able, whoever is winning.
        int highest = TrickResolver.highestOfLedSuit(trick, contract);
        List<Card> higher = new ArrayList<>();
        for (Card c : ofLedSuit) {
            if (c.strength(true) > highest) higher.add(c);
        }
        return higher.isEmpty() ? ofLedSuit : higher;
    }

    /** The player is void in the led suit. Only suit contracts impose obligations here. */
    private static List<Card> cannotFollowSuit(List<Card> hand, Trick trick, ContractType contract) {
        if (!contract.allowsRuffing()) {
            // No Trumps has no trumps at all; All Trumps has no foreign-suit ruff.
            return new ArrayList<>(hand);
        }

        Seat currentWinner = TrickResolver.winner(trick, contract);
        Seat toPlay = trick.nextToPlay();

        // Partner is winning the trick: free discard, no obligation to ruff.
        if (currentWinner != null && currentWinner.isTeammateOf(toPlay)) {
            return new ArrayList<>(hand);
        }

        List<Card> trumps = filterTrumps(hand, contract);
        if (trumps.isEmpty()) {
            return new ArrayList<>(hand);
        }

        if (TrickResolver.containsTrump(trick, contract)) {
            // Someone already trumped: must over-trump if possible, otherwise anything.
            int highest = TrickResolver.highestTrumpStrength(trick, contract);
            List<Card> higher = new ArrayList<>();
            for (Card c : trumps) {
                if (c.strength(true) > highest) higher.add(c);
            }
            return higher.isEmpty() ? new ArrayList<>(hand) : higher;
        }

        // Opponents hold the trick and nobody has trumped yet: must ruff.
        return trumps;
    }

    private static List<Card> filterSuit(List<Card> hand, Suit suit) {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (c.suit() == suit) out.add(c);
        }
        return out;
    }

    private static List<Card> filterTrumps(List<Card> hand, ContractType contract) {
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (contract.isTrumpSuit(c.suit())) out.add(c);
        }
        return out;
    }
}
