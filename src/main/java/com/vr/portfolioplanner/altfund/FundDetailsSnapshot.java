package com.vr.portfolioplanner.altfund;

/**
 * The Fund Details API (Source 2) field needed by {@link AlternateFundValidator}
 * for one {@code plan_id}: {@code category_name}. Used both to pair orphaned
 * funds across API-1/API-2 and to evaluate Rules 2 and 4 against an API-1
 * alternate.
 */
public final class FundDetailsSnapshot {

    private final String  planId;
    private final boolean found;
    private final String  categoryName;

    private FundDetailsSnapshot(String planId, boolean found, String categoryName) {
        this.planId       = planId;
        this.found        = found;
        this.categoryName = categoryName;
    }

    public static FundDetailsSnapshot found(String planId, String categoryName) {
        return new FundDetailsSnapshot(planId, true, categoryName);
    }

    public static FundDetailsSnapshot notFound(String planId) {
        return new FundDetailsSnapshot(planId, false, null);
    }

    public String  getPlanId()       { return planId; }
    public boolean isFound()         { return found; }
    public String  getCategoryName() { return categoryName; }

    @Override
    public String toString() {
        return "FundDetailsSnapshot{planId='" + planId + "', found=" + found + ", categoryName='" + categoryName + "'}";
    }
}
