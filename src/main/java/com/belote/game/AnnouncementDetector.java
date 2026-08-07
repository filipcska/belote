package com.belote.game;

import com.belote.model.Card;
import com.belote.model.ContractType;
import com.belote.model.Rank;
import com.belote.model.Seat;
import com.belote.model.Suit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds melds, carres and belote in a hand and resolves competing declarations.
 *
 * <p>Rule interpretations, all documented in the README:
 * <ul>
 *   <li>No Trumps admits no announcements whatsoever.</li>
 *   <li>A run of six or more scores as a quinte (100), not quinte + tierce.</li>
 *   <li>The team holding the best sequence keeps all of its sequences and the
 *       opposing team keeps none; exactly equal best sequences cancel both sides.</li>
 *   <li>Carres are ranked among themselves by the same winner-takes-all rule but
 *       never interact with sequences in either direction.</li>
 * </ul>
 */
public final class AnnouncementDetector {

    private AnnouncementDetector() {}

    /** Everything a single player can declare from their 8-card hand. */
    public static List<Announcement> detect(Seat seat, List<Card> hand, ContractType contract) {
        List<Announcement> out = new ArrayList<>();
        if (contract == ContractType.NO_TRUMPS) {
            return out;
        }
        out.addAll(detectSequences(seat, hand));
        out.addAll(detectCarres(seat, hand));
        out.addAll(detectBelote(seat, hand, contract));
        return out;
    }

    public static List<Announcement> detectSequences(Seat seat, List<Card> hand) {
        List<Announcement> out = new ArrayList<>();
        Map<Suit, List<Card>> bySuit = new EnumMap<>(Suit.class);
        for (Card c : hand) {
            bySuit.computeIfAbsent(c.suit(), s -> new ArrayList<>()).add(c);
        }

        for (Map.Entry<Suit, List<Card>> e : bySuit.entrySet()) {
            List<Card> cards = e.getValue();
            cards.sort(Comparator.comparingInt(c -> c.rank().sequenceOrder()));

            int runStart = 0;
            for (int i = 1; i <= cards.size(); i++) {
                boolean consecutive = i < cards.size()
                        && cards.get(i).rank().sequenceOrder()
                           == cards.get(i - 1).rank().sequenceOrder() + 1;
                if (!consecutive) {
                    int length = i - runStart;
                    if (length >= 3) {
                        Rank top = cards.get(i - 1).rank();
                        out.add(Announcement.sequence(seat, e.getKey(), top, length));
                    }
                    runStart = i;
                }
            }
        }
        return out;
    }

    public static List<Announcement> detectCarres(Seat seat, List<Card> hand) {
        List<Announcement> out = new ArrayList<>();
        Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
        for (Card c : hand) {
            counts.merge(c.rank(), 1, Integer::sum);
        }
        for (Map.Entry<Rank, Integer> e : counts.entrySet()) {
            if (e.getValue() == 4 && e.getKey().carreValue() > 0) {
                out.add(Announcement.carre(seat, e.getKey()));
            }
        }
        return out;
    }

    public static List<Announcement> detectBelote(Seat seat, List<Card> hand, ContractType contract) {
        List<Announcement> out = new ArrayList<>();
        for (Suit s : Suit.values()) {
            if (!contract.isTrumpSuit(s)) continue;
            boolean king = hand.contains(Card.of(s, Rank.KING));
            boolean queen = hand.contains(Card.of(s, Rank.QUEEN));
            if (king && queen) {
                out.add(Announcement.belote(seat, s));
            }
        }
        return out;
    }

    /**
     * Applies the priority rules across both teams.
     *
     * @return the announcements that actually score, in declaration order
     */
    public static List<Announcement> resolve(List<Announcement> all) {
        List<Announcement> sequences = new ArrayList<>();
        List<Announcement> carres = new ArrayList<>();
        List<Announcement> belotes = new ArrayList<>();

        for (Announcement a : all) {
            if (a.type().isSequence()) sequences.add(a);
            else if (a.type() == Announcement.Type.CARRE) carres.add(a);
            else belotes.add(a);
        }

        List<Announcement> surviving = new ArrayList<>();
        surviving.addAll(winnersOf(sequences, Announcement::sequenceRank));
        surviving.addAll(winnersOf(carres, Announcement::points));
        surviving.addAll(belotes);   // belote is never contested
        return surviving;
    }

    /**
     * Keeps only the announcements of the team with the strictly best entry.
     * A tie between the two teams' best entries cancels the whole category.
     */
    private static List<Announcement> winnersOf(List<Announcement> group,
                                                java.util.function.ToIntFunction<Announcement> ranker) {
        if (group.isEmpty()) return List.of();

        int[] best = {-1, -1};
        for (Announcement a : group) {
            best[a.team()] = Math.max(best[a.team()], ranker.applyAsInt(a));
        }

        if (best[0] == best[1]) return List.of();   // includes the both-absent case (-1/-1)

        int winner = best[0] > best[1] ? 0 : 1;
        List<Announcement> out = new ArrayList<>();
        for (Announcement a : group) {
            if (a.team() == winner) out.add(a);
        }
        return out;
    }

    /** Convenience: total surviving announcement points for one team. */
    public static int pointsFor(List<Announcement> resolved, int team) {
        int total = 0;
        for (Announcement a : resolved) {
            if (a.team() == team) total += a.points();
        }
        return total;
    }
}
