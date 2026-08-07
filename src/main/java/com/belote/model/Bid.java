package com.belote.model;

/** A single bidding action. */
public final class Bid {

    public enum Kind { PASS, CONTRACT, DOUBLE, REDOUBLE }

    public static final Bid PASS = new Bid(Kind.PASS, null);
    public static final Bid DOUBLE = new Bid(Kind.DOUBLE, null);
    public static final Bid REDOUBLE = new Bid(Kind.REDOUBLE, null);

    private final Kind kind;
    private final ContractType contract;

    private Bid(Kind kind, ContractType contract) {
        this.kind = kind;
        this.contract = contract;
    }

    public static Bid contract(ContractType type) { return new Bid(Kind.CONTRACT, type); }

    public Kind kind() { return kind; }
    public ContractType contract() { return contract; }

    public String key() {
        switch (kind) {
            case PASS: return "bid.pass";
            case DOUBLE: return "bid.double";
            case REDOUBLE: return "bid.redouble";
            default: return contract.key();
        }
    }

    @Override
    public String toString() { return kind == Kind.CONTRACT ? contract.name() : kind.name(); }
}
