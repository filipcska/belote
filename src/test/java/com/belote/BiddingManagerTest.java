package com.belote;

import com.belote.game.BiddingManager;
import com.belote.model.Bid;
import com.belote.model.Contract;
import com.belote.model.ContractType;
import com.belote.model.Seat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiddingManagerTest {

    private static boolean offers(BiddingManager m, Seat seat, Bid.Kind kind) {
        return m.legalBids(seat).stream().anyMatch(b -> b.kind() == kind);
    }

    @Test
    void theDealersRightHandNeighbourBidsFirst() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        assertEquals(Seat.SOUTH, m.currentBidder());
    }

    @Test
    void bidsMustClimbTheHierarchy() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));

        boolean clubsOffered = m.legalBids(Seat.EAST).stream()
                .anyMatch(b -> b.contract() == ContractType.CLUBS);
        boolean spadesOffered = m.legalBids(Seat.EAST).stream()
                .anyMatch(b -> b.contract() == ContractType.SPADES);

        assertFalse(clubsOffered, "clubs sits below hearts");
        assertTrue(spadesOffered, "spades sits above hearts");
        assertThrows(IllegalArgumentException.class,
                () -> m.place(Seat.EAST, Bid.contract(ContractType.CLUBS)));
    }

    @Test
    void allTrumpsOutranksNoTrumps() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.NO_TRUMPS));

        assertTrue(m.legalBids(Seat.EAST).stream()
                .anyMatch(b -> b.contract() == ContractType.ALL_TRUMPS));
    }

    @Test
    void onlyOpponentsMayDouble() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));

        assertTrue(offers(m, Seat.EAST, Bid.Kind.DOUBLE), "East opposes South");

        m.place(Seat.EAST, Bid.PASS);
        assertFalse(offers(m, Seat.NORTH, Bid.Kind.DOUBLE), "North is South's partner");
    }

    @Test
    void onlyTheContractTeamMayRedouble() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));
        m.place(Seat.EAST, Bid.DOUBLE);

        assertTrue(offers(m, Seat.NORTH, Bid.Kind.REDOUBLE), "North is the declarer's partner");
        m.place(Seat.NORTH, Bid.PASS);
        assertFalse(offers(m, Seat.WEST, Bid.Kind.REDOUBLE), "West is a defender");
    }

    @Test
    void doublingCannotHappenTwice() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));
        m.place(Seat.EAST, Bid.DOUBLE);
        m.place(Seat.NORTH, Bid.PASS);

        assertFalse(offers(m, Seat.WEST, Bid.Kind.DOUBLE));
    }

    @Test
    void threePassesAfterAContractCloseTheBidding() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.SPADES));
        m.place(Seat.EAST, Bid.PASS);
        m.place(Seat.NORTH, Bid.PASS);
        assertFalse(m.isComplete());

        m.place(Seat.WEST, Bid.PASS);
        assertTrue(m.isComplete());

        Contract result = m.result();
        assertEquals(ContractType.SPADES, result.type());
        assertEquals(Seat.SOUTH, result.declarer());
        assertEquals(Contract.DoubleState.NONE, result.doubleState());
    }

    @Test
    void aDoubleResetsThePassCountSoItCanBeAnswered() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));
        m.place(Seat.EAST, Bid.PASS);
        m.place(Seat.NORTH, Bid.PASS);
        m.place(Seat.WEST, Bid.DOUBLE);

        assertFalse(m.isComplete(), "the declarers must get a chance to redouble");
        assertEquals(Seat.SOUTH, m.currentBidder());
    }

    @Test
    void fourPassesLeaveTheHandUnbid() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.PASS);
        m.place(Seat.EAST, Bid.PASS);
        m.place(Seat.NORTH, Bid.PASS);
        m.place(Seat.WEST, Bid.PASS);

        assertTrue(m.isComplete());
        assertTrue(m.isPassedOut());
        assertNull(m.result());
    }

    @Test
    void aHigherBidClearsAnExistingDouble() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        m.place(Seat.SOUTH, Bid.contract(ContractType.HEARTS));
        m.place(Seat.EAST, Bid.DOUBLE);
        m.place(Seat.NORTH, Bid.contract(ContractType.ALL_TRUMPS));

        assertEquals(Contract.DoubleState.NONE, m.doubleState());
    }

    @Test
    void bidsOutOfTurnAreRejected() {
        BiddingManager m = new BiddingManager(Seat.WEST);
        assertThrows(IllegalStateException.class, () -> m.place(Seat.EAST, Bid.PASS));
    }
}
