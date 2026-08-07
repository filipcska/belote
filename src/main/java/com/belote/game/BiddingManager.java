package com.belote.game;

import com.belote.model.Bid;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Seat;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one bidding round and enforces a legal bidding sequence.
 *
 * <p>Bidding ends after three consecutive passes once a contract has been
 * proposed, or after four passes if nobody bid at all (the hand is redealt).
 * Any non-pass action resets the pass count, so a double can be answered.
 */
public final class BiddingManager {

    /** One entry in the visible bidding history. */
    public static final class Entry {
        private final Seat seat;
        private final Bid bid;

        Entry(Seat seat, Bid bid) { this.seat = seat; this.bid = bid; }

        public Seat seat() { return seat; }
        public Bid bid() { return bid; }
    }

    private final Seat dealer;
    private final List<Entry> history = new ArrayList<>();

    private Seat current;
    private ContractType highest;
    private Seat declarer;
    private Contract.DoubleState doubleState = Contract.DoubleState.NONE;
    private int consecutivePasses;
    private boolean complete;

    public BiddingManager(Seat dealer) {
        this.dealer = dealer;
        this.current = dealer.next();   // the player to the dealer's right bids first
    }

    public Seat dealer() { return dealer; }
    public Seat currentBidder() { return current; }
    public List<Entry> history() { return history; }
    public ContractType highestContract() { return highest; }
    public Seat declarer() { return declarer; }
    public Contract.DoubleState doubleState() { return doubleState; }
    public boolean isComplete() { return complete; }

    /** True when everybody passed and no contract was proposed. */
    public boolean isPassedOut() { return complete && highest == null; }

    public List<Bid> legalBids(Seat seat) {
        List<Bid> out = new ArrayList<>();
        if (complete || seat != current) return out;

        out.add(Bid.PASS);
        for (ContractType t : ContractType.values()) {
            if (highest == null || t.rank() > highest.rank()) {
                out.add(Bid.contract(t));
            }
        }
        if (canDouble(seat)) out.add(Bid.DOUBLE);
        if (canRedouble(seat)) out.add(Bid.REDOUBLE);
        return out;
    }

    private boolean canDouble(Seat seat) {
        return highest != null
                && doubleState == Contract.DoubleState.NONE
                && !seat.isTeammateOf(declarer);
    }

    private boolean canRedouble(Seat seat) {
        return highest != null
                && doubleState == Contract.DoubleState.DOUBLED
                && seat.isTeammateOf(declarer);
    }

    public void place(Seat seat, Bid bid) {
        if (complete) throw new IllegalStateException("bidding is over");
        if (seat != current) throw new IllegalStateException("not " + seat + "'s turn to bid");
        if (!isLegal(seat, bid)) throw new IllegalArgumentException("illegal bid " + bid + " by " + seat);

        history.add(new Entry(seat, bid));

        switch (bid.kind()) {
            case PASS:
                consecutivePasses++;
                break;
            case CONTRACT:
                highest = bid.contract();
                declarer = seat;
                doubleState = Contract.DoubleState.NONE;   // a higher bid clears any double
                consecutivePasses = 0;
                break;
            case DOUBLE:
                doubleState = Contract.DoubleState.DOUBLED;
                consecutivePasses = 0;
                break;
            case REDOUBLE:
                doubleState = Contract.DoubleState.REDOUBLED;
                consecutivePasses = 0;
                break;
        }

        if (highest == null && consecutivePasses >= 4) {
            complete = true;
        } else if (highest != null && consecutivePasses >= 3) {
            complete = true;
        } else {
            current = current.next();
        }
    }

    private boolean isLegal(Seat seat, Bid bid) {
        for (Bid b : legalBids(seat)) {
            if (b.kind() == bid.kind() && b.contract() == bid.contract()) return true;
        }
        return false;
    }

    /** The settled contract, or null if the hand was passed out. */
    public Contract result() {
        if (!complete || highest == null) return null;
        return new Contract(highest, declarer, doubleState);
    }
}
