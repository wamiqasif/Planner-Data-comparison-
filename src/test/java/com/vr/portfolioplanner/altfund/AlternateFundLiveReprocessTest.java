package com.vr.portfolioplanner.altfund;

import com.vr.portfolioplanner.response.model.FundEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Live reprocessing of the alternate-fund plan_ids the business asked to be
 * validated first, before the new alternate-fund logic (see
 * {@link AlternateFundValidator}) is turned on for the full Excel-driven
 * suite: {@code 16897, 38387, 15866, 16554, 15800, 19701, 16167}.
 *
 * <p>Hits the real Fund Opinion / Fund Details endpoints — requires network
 * access and is excluded from the default {@code mvn test} run (group
 * {@code altfund} only). Run with {@code mvn test -Dgroups=altfund}.
 *
 * <p>Only the {@code 38387 (API-2 original) -> 16897 (API-1 alternate)} pair
 * was given a confirmed role/pairing when this test was written. The other 5
 * plan_ids have no confirmed original/alternate role or pairing partner, so
 * this test does not guess one — it fetches and prints their raw Source 1 /
 * Source 2 data for inspection instead of running them through the full
 * pairing + rule engine.
 */
public class AlternateFundLiveReprocessTest {

    private static final Logger log = LoggerFactory.getLogger(AlternateFundLiveReprocessTest.class);

    private static final List<String> KNOWN_PLAN_IDS =
        List.of("16897", "38387", "15866", "16554", "15800", "19701", "16167");

    @Test(groups = "altfund",
          description = "Confirmed pair: API-2 original 38387 -> API-1 alternate 16897, run end-to-end")
    public void reprocess_38387_to_16897_confirmedPair() {
        String testCaseId = "ALTFUND_REPROCESS_38387_16897";
        String api2PlanId = "38387";
        String api1PlanId = "16897";

        FundEntry api2Original = FundEntry.builder()
            .planId(api2PlanId).planName("(API-2 original — name not captured from live response)")
            .categoryId("").categoryFmt("").amount(BigDecimal.ZERO).legs(List.of())
            .build();
        FundEntry api1Alternate = FundEntry.builder()
            .planId(api1PlanId).planName("Aditya Birla Sun Life Balanced Advantage Fund - Direct Plan")
            .categoryId("").categoryFmt("").amount(BigDecimal.ZERO).legs(List.of())
            .build();

        log.info("=== Alternate Fund reprocess: API-2 original {} -> API-1 alternate {} ===",
            api2PlanId, api1PlanId);

        FundOpinionSnapshot opinion;
        String opinionFetchError = null;
        try {
            opinion = FundOpinionClient.fetch(List.of(api1PlanId)).get(api1PlanId);
            log.info("Source 1 (Fund Opinion) for plan_id={} -> {}", api1PlanId, opinion);
        } catch (FundDataUnavailableException e) {
            opinion = FundOpinionSnapshot.notFound(api1PlanId);
            opinionFetchError = e.getMessage();
            log.warn("Source 1 (Fund Opinion) fetch FAILED for plan_id={}: {}", api1PlanId, e.getMessage());
        }

        FundDetailsSnapshot details;
        try {
            details = FundDetailsClient.fetch(api1PlanId);
            log.info("Source 2 (Fund Details) for plan_id={} -> {}", api1PlanId, details);
        } catch (FundDataUnavailableException e) {
            details = FundDetailsSnapshot.notFound(api1PlanId);
            log.warn("Source 2 (Fund Details) fetch FAILED for plan_id={}: {}", api1PlanId, e.getMessage());
        }

        FundDetailsSnapshot api2Details;
        try {
            api2Details = FundDetailsClient.fetch(api2PlanId);
            log.info("Source 2 (Fund Details) for plan_id={} -> {}", api2PlanId, api2Details);
        } catch (FundDataUnavailableException e) {
            api2Details = FundDetailsSnapshot.notFound(api2PlanId);
            log.warn("Source 2 (Fund Details) fetch FAILED for plan_id={}: {}", api2PlanId, e.getMessage());
        }

        AlternateFundAudit audit = AlternateFundValidator.evaluateAlternate(
            testCaseId, api2Original, api1Alternate, opinion, opinionFetchError, details, api2Details);

        log.info("Rule 1 (Opinion)          : {}", audit.getRule1());
        log.info("Rule 2 (Prohibited)       : {}", audit.getRule2());
        log.info("Rule 3 (Index Fund)       : {}", audit.getRule3());
        log.info("Rule 4 (Star Rating)      : {}", audit.getRule4());
        log.info("Rule 5 (Watchlist)        : {}", audit.getRule5());
        log.info("Failed Condition          : {}", audit.getFailedCondition());
        log.info("Final Result              : {} (Match Type: {})", audit.getFinalResult(), audit.getMatchType());
    }

    @Test(groups = "altfund",
          description = "Raw Source 1 / Source 2 data dump for the remaining plan_ids with no "
                       + "confirmed original/alternate role or pairing — no pairing is guessed")
    public void reprocess_remainingPlanIds_dataDumpOnly() {
        log.info("=== Raw data dump for reprocessing plan_ids (role/pairing not confirmed) ===");

        Map<String, FundOpinionSnapshot> opinions;
        try {
            opinions = FundOpinionClient.fetch(KNOWN_PLAN_IDS);
        } catch (FundDataUnavailableException e) {
            log.warn("Source 1 (Fund Opinion) batched fetch FAILED for {}: {}", KNOWN_PLAN_IDS, e.getMessage());
            opinions = Map.of();
        }

        for (String planId : KNOWN_PLAN_IDS) {
            FundOpinionSnapshot opinion = opinions.getOrDefault(planId, FundOpinionSnapshot.notFound(planId));
            log.info("plan_id={} | Fund Opinion -> {}", planId, opinion);

            try {
                FundDetailsSnapshot details = FundDetailsClient.fetch(planId);
                log.info("plan_id={} | Fund Details -> {}", planId, details);
            } catch (FundDataUnavailableException e) {
                log.warn("plan_id={} | Fund Details fetch FAILED: {}", planId, e.getMessage());
            }
        }

        log.info("No pairing or rule evaluation is run for these plan_ids: their API-1/API-2 role "
            + "and pairing partner were not confirmed when this test was written. Confirm each one's "
            + "role, or run 'mvn test -Dgroups=api-comparison' with live credentials to observe which "
            + "real test-case rows produce them as unmatched orphans, then extend this test.");
    }
}
