package edu.registration.eligibility;

import java.math.BigDecimal;

 
public final class FinancialHoldChecker {
    private final BigDecimal threshold;

    public FinancialHoldChecker(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public boolean hasHold(BigDecimal outstandingBalance) {
        return outstandingBalance.compareTo(threshold) > 0;
    }
}
