package com.belote.game;

import java.util.List;

/** Everything the scoreboard and the end-of-hand dialog need about one hand. */
public final class HandResult {

    public enum Outcome {
        MADE("outcome.made"),
        SET("outcome.set"),
        HANGING("outcome.hanging");

        private final String key;
        Outcome(String key) { this.key = key; }
        public String key() { return key; }
    }

    private final Outcome outcome;
    private final int declaringTeam;
    private final int[] rawPoints;        // before rounding, after doubling and bonuses
    private final int[] roundedPoints;    // raw / 10, rounded
    private final int[] awarded;          // what actually goes on the scoreboard
    private final int hangingCarried;     // frozen points brought into this hand
    private final int hangingCreated;     // frozen points leaving this hand
    private final boolean capot;
    private final int capotTeam;
    private final List<Announcement> announcements;
    private final int multiplier;

    public HandResult(Outcome outcome, int declaringTeam, int[] rawPoints, int[] roundedPoints,
                      int[] awarded, int hangingCarried, int hangingCreated,
                      boolean capot, int capotTeam, List<Announcement> announcements,
                      int multiplier) {
        this.outcome = outcome;
        this.declaringTeam = declaringTeam;
        this.rawPoints = rawPoints;
        this.roundedPoints = roundedPoints;
        this.awarded = awarded;
        this.hangingCarried = hangingCarried;
        this.hangingCreated = hangingCreated;
        this.capot = capot;
        this.capotTeam = capotTeam;
        this.announcements = announcements;
        this.multiplier = multiplier;
    }

    public Outcome outcome() { return outcome; }
    public int declaringTeam() { return declaringTeam; }
    public int[] rawPoints() { return rawPoints; }
    public int[] roundedPoints() { return roundedPoints; }
    public int[] awarded() { return awarded; }
    public int hangingCarried() { return hangingCarried; }
    public int hangingCreated() { return hangingCreated; }
    public boolean isCapot() { return capot; }
    public int capotTeam() { return capotTeam; }
    public List<Announcement> announcements() { return announcements; }
    public int multiplier() { return multiplier; }
}
