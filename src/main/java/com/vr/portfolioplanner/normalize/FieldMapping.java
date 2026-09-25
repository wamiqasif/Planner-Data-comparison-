package com.vr.portfolioplanner.normalize;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Registry of explicit field-level mappings between API-1 and API-2 responses.
 *
 * <p>This is the single authoritative reference for which JSON path in API-1
 * corresponds to which JSON path in API-2, whether the pair is comparable, and
 * what normalization (if any) must be applied before comparison.
 *
 * <p>When adding or changing a mapping, update this class and the corresponding
 * normaliser.  Do not compare fields that are absent from this registry.
 */
public final class FieldMapping {

    // -------------------------------------------------------------------------
    // Entry record
    // -------------------------------------------------------------------------

    /**
     * One explicit field-pair mapping.
     *
     * @param api1Path   JSON path within the API-1 response
     * @param api2Path   JSON path within the API-2 response
     * @param status     comparability classification
     * @param notes      normalization rules or rationale
     */
    public record Entry(
        String api1Path,
        String api2Path,
        ComparabilityStatus status,
        String notes
    ) {}

    // -------------------------------------------------------------------------
    // Canonical mappings
    // -------------------------------------------------------------------------

    public static final Entry FUND_LIST = new Entry(
        "data.funds_data.data",
        "data.funds_data.data",
        ComparabilityStatus.COMPARABLE,
        "Match by plan_id (FUND_PLAN_ID). Never compare by array index."
    );

    public static final Entry FUND_PLAN_ID = new Entry(
        "plan_data.plan_id",
        "plan_data.plan_id",
        ComparabilityStatus.COMPARABLE,
        "Primary fund identity key. Used to pair funds across APIs before field comparison."
    );

    public static final Entry FUND_NAME = new Entry(
        "plan_data.name",
        "plan_data.name",
        ComparabilityStatus.COMPARABLE,
        "Apply FundNameNormalizer: 'Dir-G' → 'Direct-G' before comparison."
    );

    public static final Entry CATEGORY_ID = new Entry(
        "category_data.category_id",
        "category_data.category_id",
        ComparabilityStatus.COMPARABLE,
        "No normalization required."
    );

    public static final Entry CATEGORY_NAME = new Entry(
        "category_data.category_fmt",
        "category_data.category_fmt",
        ComparabilityStatus.COMPARABLE,
        "No normalization required."
    );

    public static final Entry INVESTMENT_AMOUNT = new Entry(
        "first_month_amount",
        "txn_data.amount",
        ComparabilityStatus.COMPARABLE,
        "Different JSON paths; same business meaning (total first-month allocation). "
        + "Apply CurrencyNormalizer to handle formatted strings. "
        + "Use configurable numeric tolerance (comparison.amount.tolerance)."
    );

    public static final Entry TRANSACTION_TYPE = new Entry(
        "legs[].type  /  legs[].type_fmt",
        "inv_data[].txn_type_name",
        ComparabilityStatus.COMPARABLE,
        "Apply TransactionTypeNormalizer: sip/SIP → SIP, one_time/One-time → ONE_TIME. "
        + "Match legs to inv_data entries by normalized type, not by array position."
    );

    public static final Entry BREAKDOWN_LIST = new Entry(
        "breakdown_funds_data.data",
        "breakdown_funds_data.data",
        ComparabilityStatus.COMPARABLE,
        "Match breakdown entries by category_id. Do not compare by array index."
    );

    public static final Entry BREAKDOWN_PERCENTAGE = new Entry(
        "perc_fmt",
        "perc_fmt",
        ComparabilityStatus.COMPARABLE,
        "Apply CurrencyNormalizer.parsePercentage to strip '%' and convert to BigDecimal. "
        + "Fall back to numeric 'percentage' field if perc_fmt absent."
    );

    public static final Entry API_STATUS = new Entry(
        "data.status",
        "data.status",
        ComparabilityStatus.COMPARABLE,
        "Compare exact string value (success/error)."
    );

    // -------------------------------------------------------------------------
    // NOT_COMPARABLE
    // -------------------------------------------------------------------------

    public static final Entry MESSAGE = new Entry(
        "data.message",
        "(not present in API-2)",
        ComparabilityStatus.NOT_COMPARABLE,
        "API-1 only field. Cannot be compared across APIs."
    );

    public static final Entry LEG_FREQUENCY = new Entry(
        "legs[].frequency",
        "(not present in API-2 inv_data)",
        ComparabilityStatus.NOT_COMPARABLE,
        "Frequency / duration metadata is present in API-1 legs but has no "
        + "structural equivalent in API-2 inv_data. Cannot be compared."
    );

    // -------------------------------------------------------------------------
    // EXCLUDED (generated / display-only fields)
    // -------------------------------------------------------------------------

    public static final Entry PORT_BUILDER_ID = new Entry(
        "port_builder_id",
        "port_builder_id",
        ComparabilityStatus.EXCLUDED,
        "Per-request generated technical identifier. Must not be compared."
    );

    public static final Entry OUTPUT_ID = new Entry(
        "output_id",
        "output_id",
        ComparabilityStatus.EXCLUDED,
        "Per-request generated technical identifier. Must not be compared."
    );

    // -------------------------------------------------------------------------
    // Master list
    // -------------------------------------------------------------------------

    /** All registered mappings in specification order. */
    public static final List<Entry> ALL = List.of(
        FUND_LIST,
        FUND_PLAN_ID,
        FUND_NAME,
        CATEGORY_ID,
        CATEGORY_NAME,
        INVESTMENT_AMOUNT,
        TRANSACTION_TYPE,
        BREAKDOWN_LIST,
        BREAKDOWN_PERCENTAGE,
        API_STATUS,
        // --- NOT_COMPARABLE ---
        MESSAGE,
        LEG_FREQUENCY,
        // --- EXCLUDED ---
        PORT_BUILDER_ID,
        OUTPUT_ID
    );

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    public static List<Entry> getByStatus(ComparabilityStatus status) {
        return ALL.stream()
                  .filter(e -> e.status() == status)
                  .collect(Collectors.toUnmodifiableList());
    }

    public static List<Entry> getComparable()    { return getByStatus(ComparabilityStatus.COMPARABLE); }
    public static List<Entry> getNotComparable() { return getByStatus(ComparabilityStatus.NOT_COMPARABLE); }
    public static List<Entry> getExcluded()      { return getByStatus(ComparabilityStatus.EXCLUDED); }

    private FieldMapping() {}
}
