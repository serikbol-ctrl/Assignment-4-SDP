package edu.registration.finalization;

import java.math.BigDecimal;

/** Pure business rule: what does a course cost? Per-credit rate plus a flat registration fee. */
public final class TuitionCalculator {
    private final BigDecimal ratePerCredit;
    private final BigDecimal registrationFee;

    public TuitionCalculator(BigDecimal ratePerCredit, BigDecimal registrationFee) {
        this.ratePerCredit = ratePerCredit;
        this.registrationFee = registrationFee;
    }

    public BigDecimal tuitionFor(int credits) {
        return ratePerCredit.multiply(BigDecimal.valueOf(credits)).add(registrationFee);
    }
}
