package com.belote.game;

import com.belote.model.Contract;
import com.belote.model.ContractType;

import java.util.List;

/**
 * Turns the raw outcome of a hand into scoreboard points.
 *
 * <p>Rules (belot.bg), with the interpretations documented in the README:
 * <ul>
 *   <li>Made / set / hanging is decided on <em>raw</em> points, before rounding,
 *       so an exact 81–81 split in a suit contract really does hang.</li>
 *   <li>Rounding keeps the hand's total intact: the two rounded scores always add
 *       up to the rounded grand total (16 in a suit, 26 in No Trumps after
 *       doubling, 26 in All Trumps, plus premiums). The team with the larger
 *       remainder rounds up; on equal remainders the team with fewer points rounds
 *       up, and on a dead heat the declarers' hanging points round up
 *       (106–106 in a suit gives 10 and 11 hanging; 154–154 in All Trumps, 15 and 16).</li>
 *   <li>On a set contract the defenders take the sum of both teams' rounded
 *       points; the declarers take zero.</li>
 *   <li>Under a double or redouble the team with more points takes the whole
 *       multiplied hand, whoever declared; a dead heat freezes all of it.</li>
 *   <li>Hanging points are awarded to whichever team wins the following hand,
 *       and hang again if that hand also ties.</li>
 * </ul>
 */
public final class ScoreCalculator {

    public static final int LAST_TRICK_BONUS = 10;
    public static final int CAPOT_BONUS = 90;
    public static final int WINNING_THRESHOLD = 151;

    private ScoreCalculator() {}

    /**
     * @param trickPoints    raw card points captured by each team
     * @param tricksWon      number of tricks each team took (must sum to 8)
     * @param lastTrickTeam  team that won the eighth trick
     * @param resolved       announcements that survived the priority rules
     * @param hangingCarried frozen points from a previous hanging hand
     */
    public static HandResult score(Contract contract,
                                   int[] trickPoints,
                                   int[] tricksWon,
                                   int lastTrickTeam,
                                   List<Announcement> resolved,
                                   int hangingCarried) {

        boolean noTrumps = contract.type() == ContractType.NO_TRUMPS;
        int declaring = contract.declaringTeam();
        int defending = 1 - declaring;

        boolean capot = tricksWon[0] == 8 || tricksWon[1] == 8;
        int capotTeam = tricksWon[0] == 8 ? 0 : (tricksWon[1] == 8 ? 1 : -1);

        int[] raw = new int[2];
        for (int t = 0; t < 2; t++) {
            int base = trickPoints[t] + AnnouncementDetector.pointsFor(resolved, t);
            if (t == lastTrickTeam) base += LAST_TRICK_BONUS;
            // No Trumps doubles everything except the capot bonus.
            raw[t] = noTrumps ? base * 2 : base;
        }
        if (capot) {
            raw[capotTeam] += CAPOT_BONUS;
        }

        HandResult.Outcome outcome;
        if (raw[declaring] > raw[defending]) outcome = HandResult.Outcome.MADE;
        else if (raw[declaring] < raw[defending]) outcome = HandResult.Outcome.SET;
        else outcome = HandResult.Outcome.HANGING;

        int[] rounded = round(raw, declaring);

        int multiplier = contract.multiplier();
        int[] awarded = new int[2];
        int hangingCreated = 0;
        int handTotal = (rounded[0] + rounded[1]) * multiplier;

        if (multiplier > 1) {
            // Doubled or redoubled: winner takes all, whoever declared.
            if (outcome == HandResult.Outcome.HANGING) {
                hangingCreated = handTotal + hangingCarried;
            } else {
                int winner = outcome == HandResult.Outcome.MADE ? declaring : defending;
                awarded[winner] = handTotal + hangingCarried;
            }
        } else {
            switch (outcome) {
                case MADE:
                    awarded[declaring] = rounded[declaring] + hangingCarried;
                    awarded[defending] = rounded[defending];
                    break;
                case SET:
                    // Defenders sweep the whole hand, including any frozen points.
                    awarded[defending] = handTotal + hangingCarried;
                    break;
                case HANGING:
                    awarded[defending] = rounded[defending];
                    // The declarers' points freeze, and any previously frozen points stay frozen.
                    hangingCreated = rounded[declaring] + hangingCarried;
                    break;
            }
        }

        return new HandResult(outcome, declaring, raw, rounded, awarded,
                hangingCarried, hangingCreated, capot, capotTeam, resolved, multiplier);
    }

    /**
     * Divides both teams' raw points by ten so that the results add up to the
     * rounded grand total. The grand total ends in 2 (suit), 0 (No Trumps) or
     * 8 (All Trumps), so it rounds to 16, 26 and 26 before premiums.
     */
    public static int[] round(int[] raw, int declaringTeam) {
        int target = (raw[0] + raw[1] + 5) / 10;
        int[] out = {raw[0] / 10, raw[1] / 10};
        int extra = target - out[0] - out[1];   // 0, 1 or 2 teams round up

        if (extra >= 2) {
            out[0]++;
            out[1]++;
        } else if (extra == 1) {
            int r0 = raw[0] % 10;
            int r1 = raw[1] % 10;
            int up;
            if (r0 != r1) up = r0 > r1 ? 0 : 1;
            else if (raw[0] != raw[1]) up = raw[0] < raw[1] ? 0 : 1;
            else up = declaringTeam;
            out[up]++;
        }
        return out;
    }
}
