package com.belote.game;

import com.belote.model.Contract;
import com.belote.model.ContractType;

import java.util.List;

/**
 * Turns the raw outcome of a hand into scoreboard points.
 *
 * <p>Rule interpretations, documented in the README:
 * <ul>
 *   <li>Made / set / hanging is decided on <em>raw</em> points, before rounding,
 *       so an exact 81–81 split in a suit contract really does hang.</li>
 *   <li>A total ending in .5 rounds against the contracting team: the declarers
 *       round half down, the defenders round half up.</li>
 *   <li>On a set contract the defenders take the sum of both teams' rounded
 *       points; the declarers take zero.</li>
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

        int[] rounded = new int[2];
        rounded[declaring] = roundHalfDown(raw[declaring]);
        rounded[defending] = roundHalfUp(raw[defending]);

        int multiplier = contract.multiplier();
        int[] awarded = new int[2];
        int hangingCreated = 0;

        switch (outcome) {
            case MADE:
                awarded[declaring] = rounded[declaring] * multiplier + hangingCarried;
                awarded[defending] = rounded[defending] * multiplier;
                break;
            case SET:
                // Defenders sweep the whole hand, including any frozen points.
                awarded[defending] = (rounded[declaring] + rounded[defending]) * multiplier
                        + hangingCarried;
                awarded[declaring] = 0;
                break;
            case HANGING:
                awarded[defending] = rounded[defending] * multiplier;
                awarded[declaring] = 0;
                // The declarers' points freeze, and any previously frozen points stay frozen.
                hangingCreated = rounded[declaring] * multiplier + hangingCarried;
                break;
        }

        return new HandResult(outcome, declaring, raw, rounded, awarded,
                hangingCarried, hangingCreated, capot, capotTeam, resolved, multiplier);
    }

    /** Round to nearest ten, with exact halves going up. */
    public static int roundHalfUp(int raw) { return (raw + 5) / 10; }

    /** Round to nearest ten, with exact halves going down. */
    public static int roundHalfDown(int raw) { return (raw + 4) / 10; }
}
