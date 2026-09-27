package com.gearup.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentStatusTest {

    private static final BigDecimal TOTAL = new BigDecimal("300.00");

    @Test
    void nothingPaidIsUnpaid() {
        assertEquals(PaymentStatus.UNPAID, PaymentStatus.forAmounts(BigDecimal.ZERO, TOTAL));
    }

    @Test
    void somePaidIsPartial() {
        assertEquals(PaymentStatus.PARTIAL, PaymentStatus.forAmounts(new BigDecimal("100"), TOTAL));
    }

    @Test
    void fullAmountIsPaidEvenWithDifferentScale() {
        // 300 and 300.00 are equal amounts; compareTo is used, not equals.
        assertEquals(PaymentStatus.PAID, PaymentStatus.forAmounts(new BigDecimal("300"), TOTAL));
    }

    /** Closing a rental with extra fees can raise the total above what was already paid. */
    @Test
    void paidInvoiceBecomesPartialWhenTotalGrows() {
        BigDecimal totalWithDamageFee = TOTAL.add(new BigDecimal("50"));
        assertEquals(PaymentStatus.PARTIAL, PaymentStatus.forAmounts(TOTAL, totalWithDamageFee));
    }
}
