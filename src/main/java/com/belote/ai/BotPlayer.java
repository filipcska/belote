package com.belote.ai;

import com.belote.game.GameEngine;
import com.belote.game.TrickResolver;
import com.belote.model.Bid;
import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;
import com.belote.model.Trick;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Heuristic bot: a state evaluator rather than a random legal-move picker.
 *
 * <p>It tracks outstanding cards through {@link CardTracker}, cashes cards that
 * have become unbeatable, returns a partner's led suit, avoids breaking suits
 * the partner appears to control, and shifts between grabbing points and
 * starving the contract depending on which side of the contract it is on.
 */
public final class BotPlayer {

    private static final int SUIT_THRESHOLD = 22;
    private static final int ALL_TRUMPS_THRESHOLD = 26;
    private static final int NO_TRUMPS_THRESHOLD = 20;
    private static final int DOUBLE_MARGIN = 10;

    private final Seat seat;

    public BotPlayer(Seat seat) { this.seat = seat; }

    public Seat seat() { return seat; }

    // -------------------------------------------------------------- bidding

    public Bid chooseBid(GameEngine engine) {
        List<Bid> legal = engine.legalBids(seat);
        if (legal.isEmpty()) return Bid.PASS;

        List<Card> hand = engine.player(seat).hand();
        Seat declarer = engine.bidding().declarer();

        Bid best = null;
        int bestMargin = 0;
        for (Bid bid : legal) {
            if (bid.kind() != Bid.Kind.CONTRACT) continue;
            int margin = evaluate(hand, bid.contract()) - threshold(bid.contract());
            if (margin >= 0 && margin > bestMargin) {
                bestMargin = margin;
                best = bid;
            }
        }
        if (best != null) return best;

        // No contract worth bidding. Consider punishing the opponents instead.
        ContractType live = engine.bidding().highestContract();
        if (live != null && declarer != null && !seat.isTeammateOf(declarer)) {
            for (Bid bid : legal) {
                if (bid.kind() == Bid.Kind.DOUBLE
                        && evaluate(hand, live) >= threshold(live) + DOUBLE_MARGIN) {
                    return bid;
                }
            }
        }
        if (live != null && declarer != null && seat.isTeammateOf(declarer)) {
            for (Bid bid : legal) {
                if (bid.kind() == Bid.Kind.REDOUBLE
                        && evaluate(hand, live) >= threshold(live) + DOUBLE_MARGIN) {
                    return bid;
                }
            }
        }
        return Bid.PASS;
    }

    private static int threshold(ContractType type) {
        if (type == ContractType.ALL_TRUMPS) return ALL_TRUMPS_THRESHOLD;
        if (type == ContractType.NO_TRUMPS) return NO_TRUMPS_THRESHOLD;
        return SUIT_THRESHOLD;
    }

    /** Rough playing strength of a five-card hand under a given contract. */
    static int evaluate(List<Card> hand, ContractType type) {
        int score = 0;
        if (type.isSuitContract()) {
            Suit trump = type.trumpSuit();
            int trumpCount = 0;
            for (Card c : hand) {
                if (c.suit() == trump) {
                    trumpCount++;
                    score += trumpValue(c.rank());
                } else if (c.rank() == Rank.ACE) {
                    score += 5;
                } else if (c.rank() == Rank.TEN) {
                    score += 2;
                }
            }
            score += trumpCount * 3;
        } else if (type == ContractType.ALL_TRUMPS) {
            for (Card c : hand) score += trumpValue(c.rank());
        } else {
            for (Card c : hand) {
                switch (c.rank()) {
                    case ACE: score += 8; break;
                    case TEN: score += 4; break;
                    case KING: score += 2; break;
                    default: break;
                }
            }
        }
        return score;
    }

    private static int trumpValue(Rank rank) {
        switch (rank) {
            case JACK: return 10;
            case NINE: return 7;
            case ACE: return 5;
            case TEN: return 3;
            case KING: return 2;
            case QUEEN: return 2;
            default: return 1;
        }
    }

    // ----------------------------------------------------------------- play

    public Card chooseCard(GameEngine engine) {
        List<Card> legal = engine.legalMoves(seat);
        if (legal.isEmpty()) throw new IllegalStateException("no legal move for " + seat);
        if (legal.size() == 1) return legal.get(0);

        ContractType type = engine.contractType();
        Trick trick = engine.currentTrick();
        CardTracker tracker = engine.tracker();
        List<Card> hand = engine.player(seat).hand();
        boolean declaring = engine.contract().declaringTeam() == seat.team();

        return trick.isEmpty()
                ? chooseLead(legal, hand, type, tracker, declaring)
                : chooseFollow(legal, hand, type, tracker, trick, declaring);
    }

    // --- leading

    private Card chooseLead(List<Card> legal, List<Card> hand, ContractType type,
                            CardTracker tracker, boolean declaring) {

        // 1. Cash anything that can no longer be beaten, richest first.
        List<Card> boss = new ArrayList<>();
        for (Card c : legal) {
            if (tracker.isBoss(c, type, hand)) boss.add(c);
        }
        if (!boss.isEmpty()) {
            boss.sort(Comparator.comparingInt((Card c) -> c.points(type.isTrumpSuit(c.suit()))).reversed());
            return boss.get(0);
        }

        // 2. Return the partner's suit — they led it for a reason.
        Suit partnerSuit = tracker.lastLedBy(seat.partner());
        if (partnerSuit != null) {
            List<Card> inSuit = ofSuit(legal, partnerSuit);
            if (!inSuit.isEmpty()) {
                return lowest(inSuit, type);
            }
        }

        // 3. Declaring a suit contract with length: pull the opponents' trumps.
        if (declaring && type.isSuitContract()) {
            List<Card> trumps = ofSuit(legal, type.trumpSuit());
            if (trumps.size() >= 3) {
                return highest(trumps, type);
            }
        }

        // 4. Otherwise lead quietly from the longest safe suit.
        return lowest(safeLeads(legal, hand, type, tracker), type);
    }

    /**
     * Prefers suits no opponent is known to be void in, so a lead does not walk
     * straight into a ruff. Falls back to everything if no suit is safe.
     */
    private List<Card> safeLeads(List<Card> legal, List<Card> hand, ContractType type,
                                 CardTracker tracker) {
        if (!type.isSuitContract()) return legal;

        List<Card> safe = new ArrayList<>();
        for (Card c : legal) {
            if (type.isTrumpSuit(c.suit())) { safe.add(c); continue; }
            boolean risky = tracker.isKnownVoid(seat.next(), c.suit())
                    || tracker.isKnownVoid(seat.partner().next(), c.suit());
            if (!risky) safe.add(c);
        }
        return safe.isEmpty() ? legal : safe;
    }

    // --- following

    private Card chooseFollow(List<Card> legal, List<Card> hand, ContractType type,
                              CardTracker tracker, Trick trick, boolean declaring) {

        Seat winning = TrickResolver.winner(trick, type);
        boolean partnerWinning = winning != null && winning.isTeammateOf(seat);
        boolean lastToPlay = trick.size() == 3;

        if (partnerWinning) {
            Card partnerCard = trick.cardBy(winning);
            boolean safe = lastToPlay || tracker.isBoss(partnerCard, type, hand);
            // Safe to pile points onto a trick the partner already owns.
            return safe ? highestValue(legal, type) : lowestValue(legal, type);
        }

        List<Card> winners = new ArrayList<>();
        for (Card c : legal) {
            if (wouldWin(c, trick, type)) winners.add(c);
        }

        if (!winners.isEmpty()) {
            int atStake = TrickResolver.points(trick, type);
            // Take it cheaply when last to play or when the trick is worth having.
            // Defenders grab anything; declarers can afford to be a little pickier.
            if (lastToPlay || atStake >= 10 || declaring) {
                return cheapestWinner(winners, type);
            }
            return cheapestWinner(winners, type);
        }

        // Cannot win: throw the least useful card, keeping the partner's suit intact.
        return discard(legal, type, tracker);
    }

    private boolean wouldWin(Card card, Trick trick, ContractType type) {
        Trick probe = new Trick(trick.leader());
        for (Trick.Play p : trick.plays()) probe.add(p.seat(), p.card());
        probe.add(seat, card);
        return TrickResolver.winner(probe, type) == seat;
    }

    private Card discard(List<Card> legal, ContractType type, CardTracker tracker) {
        Suit partnerSuit = tracker.lastLedBy(seat.partner());
        List<Card> pool = new ArrayList<>();
        for (Card c : legal) {
            if (partnerSuit == null || c.suit() != partnerSuit) pool.add(c);
        }
        if (pool.isEmpty()) pool = legal;
        return lowestValue(pool, type);
    }

    // --- small helpers

    private static List<Card> ofSuit(List<Card> cards, Suit suit) {
        List<Card> out = new ArrayList<>();
        for (Card c : cards) {
            if (c.suit() == suit) out.add(c);
        }
        return out;
    }

    private static Card lowest(List<Card> cards, ContractType type) {
        return cards.stream()
                .min(Comparator.comparingInt(c -> c.strength(type.isTrumpSuit(c.suit()))))
                .orElseThrow();
    }

    private static Card highest(List<Card> cards, ContractType type) {
        return cards.stream()
                .max(Comparator.comparingInt(c -> c.strength(type.isTrumpSuit(c.suit()))))
                .orElseThrow();
    }

    private static Card lowestValue(List<Card> cards, ContractType type) {
        return cards.stream()
                .min(Comparator
                        .comparingInt((Card c) -> c.points(type.isTrumpSuit(c.suit())))
                        .thenComparingInt(c -> c.strength(type.isTrumpSuit(c.suit()))))
                .orElseThrow();
    }

    private static Card highestValue(List<Card> cards, ContractType type) {
        return cards.stream()
                .max(Comparator.comparingInt(c -> c.points(type.isTrumpSuit(c.suit()))))
                .orElseThrow();
    }

    /** The weakest card that still takes the trick. */
    private static Card cheapestWinner(List<Card> winners, ContractType type) {
        return winners.stream()
                .min(Comparator
                        .comparingInt((Card c) -> c.strength(type.isTrumpSuit(c.suit())))
                        .thenComparingInt(c -> c.points(type.isTrumpSuit(c.suit()))))
                .orElseThrow();
    }

    /** Convenience factory for the three seats the human does not occupy. */
    public static Map<Seat, BotPlayer> createBots() {
        Map<Seat, BotPlayer> bots = new EnumMap<>(Seat.class);
        for (Seat s : Seat.values()) {
            if (!s.isHuman()) bots.put(s, new BotPlayer(s));
        }
        return bots;
    }
}
