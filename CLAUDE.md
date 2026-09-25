# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Java 17 + Maven + TestNG + REST Assured framework that compares business responses from two
Portfolio Planner POST APIs ("API-1" and "API-2") for the same input, row by row, driven by an
Excel test-data file. It does not diff raw JSON — it extracts business fields from each API's
response shape, normalizes known format differences, then asserts equality field-by-field,
producing an Excel + HTML report of every mismatch found.

## Commands

Build / compile only:
```
mvn compile test-compile
```

Run the full TestNG suite (`testng.xml`, all `<test>` blocks in order — smoke → config → excel →
payload → extraction → normalize → reporting → compare → integration smoke → full API comparison):
```
mvn test
```

Run only one layer, by TestNG group (group names are declared per `<test>` in `testng.xml`):
```
mvn test -Dgroups=smoke            # SmokeTest — no network, verifies deps on classpath
mvn test -Dgroups=config           # ConfigReaderTest
mvn test -Dgroups=excel            # ExcelReaderTest
mvn test -Dgroups=payload          # PayloadBuilderTest
mvn test -Dgroups=extraction       # ResponseExtractorTest
mvn test -Dgroups=normalize        # NormalizerTest
mvn test -Dgroups=reporting        # ReportingTest
mvn test -Dgroups=compare          # ComparatorTest
mvn test -Dgroups=altfund          # AlternateFundLiveReprocessTest — hits live Fund Opinion/Details APIs
mvn test -Dgroups=integration      # PortfolioPlannerIntegrationTest — hits both live APIs once
mvn test -Dgroups=api-comparison   # ApiComparisonTest — full Excel-driven run (all executable rows)
```
Groups other than `smoke`/`config`/`excel`/`payload`/`extraction`/`normalize`/`reporting`/`compare`
require live API credentials (see Configuration below) — without them, calls typically come back
4xx and comparisons fail rather than erroring. `altfund` does not need Portfolio Planner credentials
but does need network access to `qaappapi.valueresearch.in`.

Run a single test method with plain TestNG unit tests (the non-Excel-driven suites):
```
mvn test -Dtest=ComparatorTest#someMethodName
```
This bypasses `testng.xml` group wiring, so it only reliably works for the plain JUnit-style unit
tests, not for `ApiComparisonTest` (which is entirely data-provider driven from Excel — there is no
per-row test method to target by name; filter rows in the Excel fixture's `Execute` column instead).

The `api-comparison` group runs against `data-provider-thread-count="5"` (set at the `<suite>`
level in `testng.xml`) — up to 5 rows execute concurrently, each row calling API-1 then API-2
sequentially before moving on.

## Configuration & credentials

`src/main/resources/config.properties` is the committed base config (non-sensitive values only).
Load order, highest priority first: environment variable → `config-<env>.properties` (when `ENV`
is set) → `config-local.properties` (git-ignored, developer-local) → `config.properties`. Env var
names are the property key uppercased with dots replaced by underscores (e.g. `api1.cookie` →
`API1_COOKIE`).

API-1 authenticates via session cookie only (`api1.cookie` / `API1_COOKIE`) — no Authorization
header. API-2 needs `api2.authorization`, `api2.auth.token`, and `api2.json.api.key` (or their env
var equivalents); never put real credentials in `config.properties` itself — use
`config-local.properties` or env vars.

**Gotcha:** the descriptive comment block above `api1.base.url` / `api1.endpoint` in
`config.properties` documents an older route (`qaappapi.valueresearch.in/api/v1/planner/generate`).
The actual *active* (uncommented) values are the source of truth for which endpoint is live — read
those directly rather than trusting the comment.

## Architecture

### Per-row pipeline (`ApiComparisonTest`, the main end-to-end test)

1. **Excel → `TestData`** — `ExcelFixtureBootstrap` generates
   `src/test/resources/testdata/PortfolioPlanner_TestData.xlsx` if absent; `ExcelReader` reads it
   into `TestData` rows and exposes `asDataProvider()` for TestNG. Only rows with `Execute=Y` run.
   Required columns are declared in `ExcelReader.REQUIRED_COLUMNS` and must stay in sync with
   `ExcelFixtureBootstrap.HEADERS`.
2. **Payload building** — `Api1JsonPayloadBuilder` builds API-1's JSON body;
   `Api2FormDataPayloadBuilder` builds API-2's multipart form-field map. Each maps a distinct set
   of `api1_*` / `api2_*` Excel columns onto the two APIs' differing field/ID names — neither
   builder hardcodes values.
3. **HTTP calls** — `Api1Client` / `Api2Client` POST via REST Assured and always return an
   `ApiResponse` (never throw): a normal HTTP outcome (`ApiResponse.of`, any status code) or a
   transport failure (`ApiResponse.ofError`, statusCode `-1`). `HeaderManager` assembles headers
   from `ConfigReader` per the credential rules above.
4. **Extraction** — `Api1ResponseExtractor` / `Api2ResponseExtractor` parse each API's differently
   shaped JSON into the shared `response.model` types (`ExtractedResponse`, `FundEntry`,
   `TransactionLeg`, `BreakdownEntry`) so the two APIs become comparable.
5. **Normalization** — `ResponseNormalizer` (never mutates its input) applies
   `FundNameNormalizer`, `CurrencyNormalizer`, `TransactionTypeNormalizer` to resolve known,
   acceptable format differences (e.g. "Dir-G" vs "Direct-G") before comparison.
6. **Comparison** — `ResponseComparator` orchestrates the full business-level diff in a fixed
   order (HTTP status → API status string → fund count → fund matching by `plan_id` → per-fund
   fields via `FundComparator` → breakdown count → breakdown matching by `category_id` → per-entry
   fields via `BreakdownComparator`). It never stops at the first mismatch, never compares array
   order (matches by ID instead), and never compares raw JSON — it returns a `ComparisonResult`
   holding every `Mismatch` found. `MismatchType.NORMALIZATION_DIFFERENCE` is informational only
   (does not fail the row); every other `MismatchType` does.

   Funds left unmatched by `plan_id` are **not** automatically `MISSING_FIELD`/`EXTRA_FIELD` — see
   **Alternate-fund validation** below.

### Alternate-fund validation (`altfund` package)

API-2 is always the original/reference fund; when API-1 offers a fund under a *different*
`plan_id` for the same slot, that API-1 fund is an alternate candidate. `AlternateFundValidator`
(called from `ResponseComparator`'s fund-matching step, replacing the retired
`GoodFundRegistry`/category_id substitution) pairs funds unmatched by `plan_id` across API-1/API-2
by `category_name` (fetched live via `FundDetailsClient`, `GET /api/v1/funds/{plan_id}`) —
**never** by `category_id`, and never by array position. A `category_name` shared by more than one
unmatched fund on either side cannot be uniquely paired and is reported `PAIRING_AMBIGUOUS`, never
guessed.

Once uniquely paired, the API-1 side is validated against 5 business rules using `FundOpinionClient`
(`GET /api/v1/funds/fund-opinion-data/get-list?plan_id=...`, fields `opinion_type_id`,
`is_analyst_pick`, `vr_rating`, `tag_name`) — API-2's fund is never rule-checked. Four of the five
rules depend on reference data (prohibited category list, index-fund category list, "not-rated"
exception list, exact watchlist `tag_name` value) that has no source anywhere in this repo yet; each
is a currently-empty, extensible constant in `FundClassificationRules` — while empty, that rule
reports `RuleOutcome.REQUIRED_DATA_NOT_AVAILABLE` rather than guessing PASS/FAIL. A pairing/rule
outcome of `REQUIRED_DATA_NOT_AVAILABLE` or `PAIRING_AMBIGUOUS` still fails the row (never silently
PASS). Final `MatchType` values: `DIRECT_MATCH`, `ALTERNATE_MATCH`, `ALTERNATE_REJECTED`,
`PAIRING_AMBIGUOUS`, `REQUIRED_DATA_NOT_AVAILABLE`. Every alternate-candidate fund produces an
`AlternateFundAudit` row, reported in the Excel "Alternate Fund Validation" / "Data Gaps" sheets and
an Extent report node.
7. **Reporting** — results accumulate in `ResultCollector` as `TestCaseResult` objects; after the
   suite, `ExcelResultWriter` writes `target/reports/API_Comparison_Result.xlsx` (Summary /
   Mismatch Details / Overall Summary sheets) and `ExtentReportManager` writes
   `target/reports/PortfolioPlanner_ExtentReport.html`. `ComparisonReportFormatter` builds the
   human-readable console block and populates each row's Extent entry.

### Package map

- `client` — HTTP layer only (`Api1Client`, `Api2Client`, `ApiResponse`). No parsing/business logic.
- `payload` — Excel row → outbound request body/fields, per API.
- `response` / `response.model` — inbound JSON → shared comparable model.
- `normalize` — format-difference resolution before comparison.
- `compare` — the diff engine and its result types (`ComparisonResult`, `Mismatch`, `MismatchType`).
- `altfund` — alternate-fund pairing (by `category_name`) and 5-rule business validation when API-1
  offers a different `plan_id` than API-2 for the same slot. See **Alternate-fund validation** above.
- `report` — result aggregation (`ResultCollector`, `TestCaseResult`) and both report writers.
- `config` — `ConfigReader` (properties/env precedence) and `HeaderManager` (header assembly).
- `excel` — `ExcelReader` (parses the fixture) — fixture generation itself lives in
  `src/test/.../excel/ExcelFixtureBootstrap.java` (test-scope, not shipped).
- `model` — `TestData`, the one Excel-row POJO shared across the whole pipeline.

Each non-trivial class carries a substantial class-level Javadoc explaining its contract — read
that first before changing behavior in `client`, `compare`, `normalize`, or `report`.
