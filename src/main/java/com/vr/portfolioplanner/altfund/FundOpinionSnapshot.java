package com.vr.portfolioplanner.altfund;

/**
 * The Fund Opinion API (Source 1) fields needed by {@link AlternateFundValidator}
 * for one {@code plan_id}: {@code opinion_type_id}, {@code is_analyst_pick},
 * {@code vr_rating}, {@code tag_name}.
 */
public final class FundOpinionSnapshot {

    private final String  planId;
    private final boolean found;
    private final Integer opinionTypeId;
    private final Boolean isAnalystPick;
    private final Integer vrRating;
    private final String  tagName;

    private FundOpinionSnapshot(String planId, boolean found, Integer opinionTypeId,
                                 Boolean isAnalystPick, Integer vrRating, String tagName) {
        this.planId        = planId;
        this.found         = found;
        this.opinionTypeId = opinionTypeId;
        this.isAnalystPick = isAnalystPick;
        this.vrRating      = vrRating;
        this.tagName       = tagName;
    }

    /** The {@code plan_id} was present in the fund-opinion-data response. */
    public static FundOpinionSnapshot found(String planId, Integer opinionTypeId, Boolean isAnalystPick,
                                             Integer vrRating, String tagName) {
        return new FundOpinionSnapshot(planId, true, opinionTypeId, isAnalystPick, vrRating, tagName);
    }

    /** The fetch succeeded but this {@code plan_id} was not in the response — a real negative fact, not a data gap. */
    public static FundOpinionSnapshot notFound(String planId) {
        return new FundOpinionSnapshot(planId, false, null, null, null, null);
    }

    public String  getPlanId()        { return planId; }
    public boolean isFound()          { return found; }
    public Integer getOpinionTypeId() { return opinionTypeId; }
    public Boolean getIsAnalystPick() { return isAnalystPick; }
    public Integer getVrRating()      { return vrRating; }
    public String  getTagName()       { return tagName; }

    @Override
    public String toString() {
        return "FundOpinionSnapshot{planId='" + planId + "', found=" + found
            + ", opinionTypeId=" + opinionTypeId + ", isAnalystPick=" + isAnalystPick
            + ", vrRating=" + vrRating + ", tagName='" + tagName + "'}";
    }
}
