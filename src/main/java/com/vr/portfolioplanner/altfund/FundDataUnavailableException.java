package com.vr.portfolioplanner.altfund;

/**
 * Thrown by {@link FundOpinionClient} / {@link FundDetailsClient} when a live
 * data fetch fails (network error, non-2xx, or parse failure) — distinct from
 * a successful fetch that simply doesn't contain the requested {@code plan_id}
 * (a legitimate negative business fact, represented by
 * {@code found=false} on the snapshot types instead).
 */
public final class FundDataUnavailableException extends RuntimeException {

    public FundDataUnavailableException(String message) {
        super(message);
    }

    public FundDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
