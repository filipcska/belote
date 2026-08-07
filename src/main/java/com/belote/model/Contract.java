package com.belote.model;

/** The settled contract for a hand: what was bid, by whom, and the double state. */
public final class Contract {

    public enum DoubleState {
        NONE(1), DOUBLED(2), REDOUBLED(4);

        private final int multiplier;
        DoubleState(int multiplier) { this.multiplier = multiplier; }
        public int multiplier() { return multiplier; }
    }

    private final ContractType type;
    private final Seat declarer;
    private final DoubleState doubleState;

    public Contract(ContractType type, Seat declarer, DoubleState doubleState) {
        this.type = type;
        this.declarer = declarer;
        this.doubleState = doubleState;
    }

    public ContractType type() { return type; }
    public Seat declarer() { return declarer; }
    public DoubleState doubleState() { return doubleState; }

    public int declaringTeam() { return declarer.team(); }
    public int multiplier() { return doubleState.multiplier(); }

    public Contract withDoubleState(DoubleState s) { return new Contract(type, declarer, s); }
}
