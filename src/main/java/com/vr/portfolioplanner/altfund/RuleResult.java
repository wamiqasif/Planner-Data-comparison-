package com.vr.portfolioplanner.altfund;

import java.util.Objects;

/** Immutable outcome + human-readable reason for one alternate-fund business rule. */
public final class RuleResult {

    private final RuleOutcome outcome;
    private final String      reason;

    private RuleResult(RuleOutcome outcome, String reason) {
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.reason  = reason;
    }

    public static RuleResult pass(String reason)           { return new RuleResult(RuleOutcome.PASS, reason); }
    public static RuleResult fail(String reason)            { return new RuleResult(RuleOutcome.FAIL, reason); }
    public static RuleResult notApplicable(String reason)   { return new RuleResult(RuleOutcome.NOT_APPLICABLE, reason); }
    public static RuleResult dataUnavailable(String reason) { return new RuleResult(RuleOutcome.REQUIRED_DATA_NOT_AVAILABLE, reason); }

    public RuleOutcome getOutcome() { return outcome; }
    public String       getReason() { return reason; }

    @Override
    public String toString() {
        return reason != null ? outcome + " (" + reason + ")" : outcome.toString();
    }
}
