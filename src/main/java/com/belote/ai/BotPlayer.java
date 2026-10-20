package com.belote.ai;

import com.belote.game.GameEngine;
import com.belote.game.TrickResolver;
import com.belote.model.Bid;
import com.belote.model.Card;
import com.belote.model.Contract;
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
 * <p>It tracks outstanding cards through {@link CardTracker}, leads a suit its
 * partner signalled, leads to pull an opponent's high card under a contract its
 * partner called, signals when its whole hand would take the remaining tricks,
 * cashes cards that can no longer be beaten or ruffed, returns a partner's led
 * suit, keeps its trumps and boss cards out of tricks it does not need them
 * for, and shifts between grabbing points and starving the contract depending
 * on which side of the contract it is on.
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
        return chooseCard(legal, engine.player(seat).hand(), engine.contract(),
                engine.currentTrick(), engine.tracker());
    }

    /** The play decision on explicit state, so it can be exercised without a full engine. */
    Card chooseCard(List<Card> legal, List<Card> hand, Contract contract, Trick trick,
                    CardTracker tracker) {
        if (legal.size() == 1) return legal.get(0);

        ContractType type = contract.type();
        boolean declaring = contract.declaringTeam() == seat.team();
        boolean partnerCalled = contract.declarer() == seat.partner();

        return trick.isEmpty()
                ? chooseLead(legal, hand, type, tracker, declaring, partnerCalled)
                : chooseFollow(legal, hand, type, tracker, trick, declaring);
    }

    // --- leading

    private Card chooseLead(List<Card> legal, List<Card> hand, ContractType type,
                            CardTracker tracker, boolean declaring, boolean partnerCalled) {

        // 1. The partner signalled a suit that takes the remaining tricks: lead it
        //    before cashing our own winners would force them to discard theirs.
        Suit signalled = liveSuit(tracker.signalledBy(seat.partner()), hand, type, tracker);
        if (signalled != null) {
            List<Card> inSuit = ofSuit(legal, signalled);
            if (!inSuit.isEmpty()) return lowest(inSuit, type);
        }

        // 2. The partner called the contract: lead a card that can pull an opponent's
        //    higher one, so the caller, who most likely holds the top card, takes both.
        if (partnerCalled) {
            Card draw = drawingLead(legal, hand, type, tracker);
            if (draw != null) return draw;
        }

        // 3. Cash anything that can no longer be beaten or ruffed, richest first.
        List<Card> boss = new ArrayList<>();
        for (Card c : legal) {
            if (safeToCash(c, hand, type, tracker)) boss.add(c);
        }
        if (!boss.isEmpty()) {
            boss.sort(Comparator.comparingInt((Card c) -> c.points(type.isTrumpSuit(c.suit()))).reversed());
            return boss.get(0);
        }

        // 4. Return the partner's suit — they led it for a reason.
        Suit partnerSuit = liveSuit(tracker.lastLedBy(seat.partner()), hand, type, tracker);
        if (partnerSuit != null) {
            List<Card> inSuit = ofSuit(legal, partnerSuit);
            if (!inSuit.isEmpty()) {
                return lowest(inSuit, type);
            }
        }

        // 5. Declaring a suit contract with length: pull the opponents' trumps.
        if (declaring && type.isSuitContract()) {
            List<Card> trumps = ofSuit(legal, type.trumpSuit());
            if (trumps.size() >= 3) {
                return highest(trumps, type);
            }
        }

        // 6. Otherwise lead quietly from the longest safe suit.
        return lowest(safeLeads(legal, hand, type, tracker), type);
    }

    /**
     * The top card of a suit with exactly two unseen cards above it: the caller's
     * presumed top card and one an opponent may be forced, or tempted, to spend on
     * it. Example: partner called spades, we hold the ace, the jack and nine are
     * out; the ace goes, the next opponent covers with the nine, the caller takes
     * both with the jack. In a suit contract only the trump suit qualifies.
     */
    private Card drawingLead(List<Card> legal, List<Card> hand, ContractType type,
                             CardTracker tracker) {
        Card best = null;
        for (Card c : legal) {
            if (type.isSuitContract() && !type.isTrumpSuit(c.suit())) continue;
            if (!c.equals(highest(ofSuit(hand, c.suit()), type))) continue;
            if (tracker.unseenAbove(c, type, hand) != 2) continue;
            // Pointless if the caller cannot hold the top card or no opponent can cover.
            if (tracker.isKnownVoid(seat.partner(), c.suit())) continue;
            if (tracker.isKnownVoid(seat.next(), c.suit())
                    && tracker.isKnownVoid(seat.partner().next(), c.suit())) continue;
            if (best == null || c.points(type.isTrumpSuit(c.suit()))
                    > best.points(type.isTrumpSuit(best.suit()))) {
                best = c;
            }
        }
        return best;
    }

    /** A boss card that an opponent is not known to be able to ruff. */
    private boolean safeToCash(Card card, List<Card> hand, ContractType type, CardTracker tracker) {
        if (!tracker.isBoss(card, type, hand)) return false;
        if (!type.isSuitContract() || type.isTrumpSuit(card.suit())) return true;
        if (tracker.outstanding(type.trumpSuit(), hand) == 0) return true;
        // With fewer than two cards of the suit left, somebody is out of it and may ruff.
        return tracker.outstanding(card.suit(), hand) >= 2 && !opponentMayRuff(card.suit(), hand, type, tracker);
    }

    /** True when an opponent is known to be void in {@code suit} but may still hold trumps. */
    private boolean opponentMayRuff(Suit suit, List<Card> hand, ContractType type, CardTracker tracker) {
        if (!type.isSuitContract() || type.isTrumpSuit(suit)) return false;
        Suit trump = type.trumpSuit();
        if (tracker.outstanding(trump, hand) == 0) return false;
        for (Seat opponent : List.of(seat.next(), seat.partner().next())) {
            if (tracker.isKnownVoid(opponent, suit) && !tracker.isKnownVoid(opponent, trump)) return true;
        }
        return false;
    }

    /** A suit the partner wants, unless it has run dry for them or would walk into a ruff. */
    private Suit liveSuit(Suit suit, List<Card> hand, ContractType type, CardTracker tracker) {
        if (suit == null) return null;
        if (tracker.isKnownVoid(seat.partner(), suit)) return null;
        if (tracker.outstanding(suit, hand) == 0) return null;
        if (opponentMayRuff(suit, hand, type, tracker)) return null;
        return suit;
    }

    /**
     * Prefers suits no opponent is known to be able to ruff, so a lead does not walk
     * straight into a ruff. Falls back to everything if no suit is safe.
     */
    private List<Card> safeLeads(List<Card> legal, List<Card> hand, ContractType type,
                                 CardTracker tracker) {
        if (!type.isSuitContract()) return legal;

        List<Card> safe = new ArrayList<>();
        for (Card c : legal) {
            if (!opponentMayRuff(c.suit(), hand, type, tracker)) safe.add(c);
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
            boolean safe = lastToPlay || partnerKeepsTrick(partnerCard, hand, type, tracker);
            if (safe) {
                Card signal = runningSuitSignal(legal, hand, type, tracker, trick);
                if (signal != null) return signal;
            }
            // Safe to pile points onto a trick the partner already owns, but never
            // the trumps or boss cards that would win tricks of their own.
            List<Card> spare = spare(legal, hand, type, tracker, trick);
            return safe ? highestValue(spare, type) : lowestValue(spare, type);
        }

        List<Card> winners = new ArrayList<>();
        List<Card> losers = new ArrayList<>();
        for (Card c : legal) {
            (wouldWin(c, trick, type) ? winners : losers).add(c);
        }

        if (!winners.isEmpty()) {
            Card cheapest = cheapestWinner(winners, type);
            int atStake = TrickResolver.points(trick, type);
            // Defenders grab anything. Declarers let a cheap trick go before the last
            // seat rather than spend a trump or a boss card that will win later anyway.
            boolean duck = declaring && !lastToPlay && atStake < 10 && !losers.isEmpty()
                    && isKeeper(cheapest, hand, type, tracker, trick);
            return duck ? discard(losers, hand, type, tracker, trick) : cheapest;
        }

        // Cannot win: throw the least useful card, keeping the partner's suit intact.
        return discard(legal, hand, type, tracker, trick);
    }

    /**
     * When every card in the hand would win its trick, drops the top card of a
     * suit the partner could still lead on the partner's safe trick: it tells the
     * partner to lead that suit so we take the rest, and banks the card's points
     * even if they cannot. Example: All Trumps, we hold J and 9 of hearts plus the
     * last two clubs, so the jack of hearts goes.
     */
    private Card runningSuitSignal(List<Card> legal, List<Card> hand, ContractType type,
                                   CardTracker tracker, Trick trick) {
        if (hand.size() < 2) return null;
        for (Card c : hand) {
            if (!tracker.isBoss(c, type, hand)) return null;
            if (type.isSuitContract() && !type.isTrumpSuit(c.suit())
                    && tracker.outstanding(type.trumpSuit(), hand) > 0) return null;
        }

        // Signal a suit we still hold after the drop and the partner may hold too;
        // between those, bank the most points.
        Card best = null;
        for (Suit suit : Suit.values()) {
            List<Card> inSuit = ofSuit(hand, suit);
            if (inSuit.size() < 2 || suit == trick.ledSuit()) continue;
            if (tracker.outstanding(suit, hand) == 0 || tracker.isKnownVoid(seat.partner(), suit)) continue;
            Card top = highest(inSuit, type);
            if (!legal.contains(top)) continue;
            if (best == null || top.points(type.isTrumpSuit(suit))
                    > best.points(type.isTrumpSuit(best.suit()))) {
                best = top;
            }
        }
        return best;
    }

    /** Whether the partner's winning card survives the opponent still to play after us. */
    private boolean partnerKeepsTrick(Card partnerCard, List<Card> hand, ContractType type,
                                      CardTracker tracker) {
        if (!tracker.isBoss(partnerCard, type, hand)) return false;
        if (!type.isSuitContract() || type.isTrumpSuit(partnerCard.suit())) return true;
        Suit trump = type.trumpSuit();
        Seat behind = seat.next();
        return tracker.outstanding(trump, hand) == 0
                || !tracker.isKnownVoid(behind, partnerCard.suit())
                || tracker.isKnownVoid(behind, trump);
    }

    /**
     * A card worth holding on to: a boss card, or in a suit contract a trump we
     * would otherwise spend outside a trump trick.
     */
    private boolean isKeeper(Card card, List<Card> hand, ContractType type, CardTracker tracker,
                             Trick trick) {
        if (tracker.isBoss(card, type, hand)) return true;
        return type.isSuitContract() && type.isTrumpSuit(card.suit())
                && !type.isTrumpSuit(trick.ledSuit());
    }

    /** The legal cards that are not keepers, or all of them if every one is. */
    private List<Card> spare(List<Card> legal, List<Card> hand, ContractType type,
                             CardTracker tracker, Trick trick) {
        List<Card> out = new ArrayList<>();
        for (Card c : legal) {
            if (!isKeeper(c, hand, type, tracker, trick)) out.add(c);
        }
        return out.isEmpty() ? legal : out;
    }

    private boolean wouldWin(Card card, Trick trick, ContractType type) {
        Trick probe = new Trick(trick.leader());
        for (Trick.Play p : trick.plays()) probe.add(p.seat(), p.card());
        probe.add(seat, card);
        return TrickResolver.winner(probe, type) == seat;
    }

    private Card discard(List<Card> legal, List<Card> hand, ContractType type,
                         CardTracker tracker, Trick trick) {
        Suit partnerSuit = liveSuit(tracker.signalledBy(seat.partner()), hand, type, tracker);
        if (partnerSuit == null) {
            partnerSuit = liveSuit(tracker.lastLedBy(seat.partner()), hand, type, tracker);
        }
        List<Card> pool = new ArrayList<>();
        for (Card c : spare(legal, hand, type, tracker, trick)) {
            if (partnerSuit == null || c.suit() != partnerSuit) pool.add(c);
        }
        if (pool.isEmpty()) pool = spare(legal, hand, type, tracker, trick);
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
