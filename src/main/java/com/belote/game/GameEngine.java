package com.belote.game;

import com.belote.ai.CardTracker;
import com.belote.model.Bid;
import com.belote.model.Card;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Deck;
import com.belote.model.Player;
import com.belote.model.Seat;
import com.belote.model.Trick;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Owns all mutable game state and drives the hand from deal to score.
 *
 * <p>The engine never talks to the UI directly; it emits events through
 * {@link Listener} so the same engine can be driven by tests headlessly.
 */
public final class GameEngine {

    public enum Phase { BIDDING, PLAYING, HAND_OVER, GAME_OVER }

    /** UI hook. Every callback fires on whatever thread advanced the game. */
    public interface Listener {
        default void onStateChanged() {}
        default void onHandDealt() {}
        default void onBid(Seat seat, Bid bid) {}
        default void onContractSettled(Contract contract) {}
        default void onPassedOut() {}
        default void onAnnouncement(Announcement announcement) {}
        default void onCardPlayed(Seat seat, Card card) {}
        default void onTrickComplete(Trick trick, Seat winner) {}
        default void onHandComplete(HandResult result) {}
        default void onGameOver(int winningTeam) {}
    }

    private final Map<Seat, Player> players = new EnumMap<>(Seat.class);
    private final List<Listener> listeners = new ArrayList<>();
    private final Random random;
    private final Deck deck = new Deck();
    private final CardTracker tracker = new CardTracker();

    private Seat dealer = Seat.WEST;   // so that South, to the dealer's right, starts the first hand
    private Phase phase = Phase.BIDDING;

    private BiddingManager bidding;
    private Contract contract;

    private Trick currentTrick;
    private final List<Trick> completedTricks = new ArrayList<>();
    private final int[] trickPoints = new int[2];
    private final int[] tricksWon = new int[2];
    private Seat lastTrickWinner;

    private final List<Announcement> detected = new ArrayList<>();
    private final List<Announcement> declared = new ArrayList<>();
    private final Set<Seat> hasDeclared = new LinkedHashSet<>();

    private final int[] gameScore = new int[2];
    private int hangingPoints;
    private HandResult lastResult;
    private int winningTeam = -1;

    public GameEngine() { this(new Random()); }

    public GameEngine(Random random) {
        this.random = random;
        for (Seat s : Seat.values()) {
            players.put(s, new Player(s, !s.isHuman()));
        }
    }

    // ---------------------------------------------------------------- state

    public void addListener(Listener l) { listeners.add(l); }

    public Player player(Seat seat) { return players.get(seat); }
    public Phase phase() { return phase; }
    public Seat dealer() { return dealer; }
    public BiddingManager bidding() { return bidding; }
    public Contract contract() { return contract; }
    public ContractType contractType() { return contract == null ? null : contract.type(); }
    public Trick currentTrick() { return currentTrick; }
    public List<Trick> completedTricks() { return completedTricks; }
    public CardTracker tracker() { return tracker; }
    public int[] gameScore() { return gameScore; }
    public int[] trickPoints() { return trickPoints; }
    public int[] tricksWon() { return tricksWon; }
    public int hangingPoints() { return hangingPoints; }
    public HandResult lastResult() { return lastResult; }
    public int winningTeam() { return winningTeam; }
    public List<Announcement> declaredAnnouncements() { return declared; }

    /** Whose action the game is waiting for, or null when nobody is on turn. */
    public Seat activeSeat() {
        if (phase == Phase.BIDDING) return bidding == null ? null : bidding.currentBidder();
        if (phase == Phase.PLAYING) return currentTrick == null ? null : currentTrick.nextToPlay();
        return null;
    }

    // ---------------------------------------------------------------- setup

    public void newGame() {
        gameScore[0] = 0;
        gameScore[1] = 0;
        hangingPoints = 0;
        winningTeam = -1;
        lastResult = null;
        dealer = Seat.WEST;
        startHand();
    }

    /** Deals a fresh hand: three cards each, then two, then bidding opens. */
    public void startHand() {
        for (Player p : players.values()) p.clearHand();
        completedTricks.clear();
        detected.clear();
        declared.clear();
        hasDeclared.clear();
        tracker.reset();
        trickPoints[0] = trickPoints[1] = 0;
        tricksWon[0] = tricksWon[1] = 0;
        lastTrickWinner = null;
        currentTrick = null;
        contract = null;

        deck.reset();
        deck.shuffle(random);
        dealRound(3);
        dealRound(2);

        bidding = new BiddingManager(dealer);
        phase = Phase.BIDDING;

        fire(l -> l.onHandDealt());
        fire(l -> l.onStateChanged());
    }

    private void dealRound(int count) {
        Seat s = dealer.next();
        for (int i = 0; i < 4; i++) {
            players.get(s).receive(deck.deal(count));
            s = s.next();
        }
    }

    // -------------------------------------------------------------- bidding

    public List<Bid> legalBids(Seat seat) {
        return phase == Phase.BIDDING ? bidding.legalBids(seat) : List.of();
    }

    public void placeBid(Seat seat, Bid bid) {
        if (phase != Phase.BIDDING) throw new IllegalStateException("not bidding");
        bidding.place(seat, bid);
        fire(l -> l.onBid(seat, bid));

        if (bidding.isComplete()) {
            if (bidding.isPassedOut()) {
                dealer = dealer.next();          // deal passes counter-clockwise
                fire(l -> l.onPassedOut());
                startHand();
                return;
            }
            settleContract();
        }
        fire(l -> l.onStateChanged());
    }

    private void settleContract() {
        contract = bidding.result();
        dealRound(3);                            // the final three cards
        for (Player p : players.values()) {
            p.sortForDisplay(contract.type());
            detected.addAll(AnnouncementDetector.detect(p.seat(), p.hand(), contract.type()));
        }
        currentTrick = new Trick(dealer.next()); // the dealer's right-hand neighbour leads
        phase = Phase.PLAYING;
        fire(l -> l.onContractSettled(contract));
    }

    // ----------------------------------------------------------------- play

    public List<Card> legalMoves(Seat seat) {
        if (phase != Phase.PLAYING || activeSeat() != seat) return List.of();
        return RuleValidator.legalMoves(players.get(seat).hand(), currentTrick, contract.type());
    }

    public void playCard(Seat seat, Card card) {
        if (phase != Phase.PLAYING) throw new IllegalStateException("not playing");
        if (activeSeat() != seat) throw new IllegalStateException("not " + seat + "'s turn");
        if (!legalMoves(seat).contains(card)) {
            throw new IllegalArgumentException("illegal card " + card + " for " + seat);
        }

        declareFor(seat);

        players.get(seat).remove(card);
        tracker.recordPlay(seat, card, currentTrick);
        currentTrick.add(seat, card);
        fire(l -> l.onCardPlayed(seat, card));

        if (currentTrick.isComplete()) {
            completeTrick();
        }
        fire(l -> l.onStateChanged());
    }

    /** Reveals a player's announcements as they play their first card of the hand. */
    private void declareFor(Seat seat) {
        if (!hasDeclared.add(seat)) return;
        for (Announcement a : detected) {
            if (a.seat() == seat) {
                declared.add(a);
                fire(l -> l.onAnnouncement(a));
            }
        }
    }

    private void completeTrick() {
        Seat winner = TrickResolver.winner(currentTrick, contract.type());
        int points = TrickResolver.points(currentTrick, contract.type());
        trickPoints[winner.team()] += points;
        tricksWon[winner.team()]++;
        lastTrickWinner = winner;
        completedTricks.add(currentTrick);

        Trick finished = currentTrick;
        fire(l -> l.onTrickComplete(finished, winner));

        if (completedTricks.size() == 8) {
            finishHand();
        } else {
            currentTrick = new Trick(winner);
        }
    }

    private void finishHand() {
        currentTrick = null;
        phase = Phase.HAND_OVER;

        List<Announcement> resolved = AnnouncementDetector.resolve(declared);
        lastResult = ScoreCalculator.score(contract, trickPoints, tricksWon,
                lastTrickWinner.team(), resolved, hangingPoints);

        gameScore[0] += lastResult.awarded()[0];
        gameScore[1] += lastResult.awarded()[1];
        hangingPoints = lastResult.hangingCreated();

        fire(l -> l.onHandComplete(lastResult));

        int winner = decideWinner(lastResult);
        if (winner >= 0) {
            winningTeam = winner;
            phase = Phase.GAME_OVER;
            fire(l -> l.onGameOver(winner));
        }
    }

    /** Returns the winning team, or -1 if the game continues. */
    private int decideWinner(HandResult result) {
        // A capot hand can never end the game.
        if (result.isCapot()) return -1;

        boolean t0 = gameScore[0] >= ScoreCalculator.WINNING_THRESHOLD;
        boolean t1 = gameScore[1] >= ScoreCalculator.WINNING_THRESHOLD;
        if (!t0 && !t1) return -1;
        if (t0 && !t1) return 0;
        if (t1 && !t0) return 1;
        if (gameScore[0] == gameScore[1]) return -1;   // dead heat: play another hand
        return gameScore[0] > gameScore[1] ? 0 : 1;
    }

    /** Moves the deal on and starts the next hand. */
    public void nextHand() {
        if (phase != Phase.HAND_OVER) throw new IllegalStateException("hand is not over");
        dealer = dealer.next();
        startHand();
    }

    // --------------------------------------------------------------- events

    private void fire(java.util.function.Consumer<Listener> event) {
        for (Listener l : new ArrayList<>(listeners)) {
            event.accept(l);
        }
    }
}
