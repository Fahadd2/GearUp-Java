package com.gearup.service;

import com.gearup.auth.StaffUser;
import com.gearup.db.Database;
import com.gearup.db.Transaction;
import com.gearup.exception.BadRequestException;
import com.gearup.exception.NotFoundException;
import com.gearup.model.LabeledEnum;
import com.gearup.model.PaymentMethod;
import com.gearup.model.PaymentRequest;
import com.gearup.model.PaymentResult;
import com.gearup.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Recording payments against invoices (staff only).
 *
 * <p>The invoice row is locked with {@code SELECT ... FOR UPDATE} before the balance is checked,
 * so two payments recorded at the same moment cannot together pay more than the invoice total.
 */
public final class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern INVOICE_ID = Pattern.compile("INV-\\d+");
    private static final int MAX_DECIMALS = 2;

    private final Database database;

    public PaymentService(Database database) {
        this.database = database;
    }

    private record LockedInvoice(BigDecimal total, PaymentStatus status) {
    }

    /**
     * Records one payment and updates the invoice's payment status.
     *
     * @throws BadRequestException if the input is invalid, the invoice is already paid,
     *                             or the amount is more than what is still owed
     * @throws NotFoundException   if the invoice does not exist
     */
    public PaymentResult pay(PaymentRequest request, StaffUser staff) {
        String invoiceId = Validation.requireFormat(request.invoiceId(), "invoice_id", INVOICE_ID, "INV-5");
        PaymentMethod method = Validation.requireValue(request.method(), "method");
        BigDecimal amount = Validation.requireValue(request.amount(), "amount");
        if (amount.signum() <= 0) {
            throw new BadRequestException("amount must be greater than 0");
        }
        if (amount.stripTrailingZeros().scale() > MAX_DECIMALS) {
            throw new BadRequestException("amount can have at most " + MAX_DECIMALS + " decimal places");
        }

        PaymentResult result = database.inTransaction(tx -> {
            LockedInvoice invoice = tx.queryOne(
                            "SELECT total_amount, payment_status FROM public.invoices WHERE inv_id = ? FOR UPDATE",
                            row -> new LockedInvoice(row.getBigDecimal("total_amount"),
                                    LabeledEnum.fromLabel(PaymentStatus.class, row.getString("payment_status"))),
                            invoiceId)
                    .orElseThrow(() -> new NotFoundException("Invoice " + invoiceId + " not found. Please check the invoice ID."));
            if (invoice.status() == PaymentStatus.PAID) {
                throw new BadRequestException("The invoice is already paid.");
            }

            BigDecimal alreadyPaid = sumPayments(tx, invoiceId);
            BigDecimal remaining = invoice.total().subtract(alreadyPaid);
            if (amount.compareTo(remaining) > 0) {
                throw new BadRequestException("Payment amount (" + sar(amount) + ") exceeds remaining balance ("
                        + sar(remaining) + "). Invoice total: " + sar(invoice.total()) + ".");
            }

            tx.update("""
                    INSERT INTO public.payments (invoice_id, method, amount, reference, recorded_by_emp_id)
                    VALUES (?, ?::payment_method, ?, ?, ?)""",
                    invoiceId, method, amount, request.reference(), staff.empId());

            BigDecimal paidTotal = alreadyPaid.add(amount);
            PaymentStatus newStatus = PaymentStatus.forAmounts(paidTotal, invoice.total());
            tx.update("UPDATE public.invoices SET payment_status = ?::payment_status WHERE inv_id = ?",
                    newStatus, invoiceId);
            return new PaymentResult(true, invoiceId, newStatus, paidTotal);
        });

        log.info("Payment of {} ({}) recorded on invoice {} by {}; invoice is now {}",
                sar(amount), method.label(), invoiceId, staff.empId(), result.status().label());
        return result;
    }

    private static BigDecimal sumPayments(Transaction tx, String invoiceId) throws SQLException {
        return tx.queryOne("SELECT COALESCE(SUM(amount), 0) AS paid FROM public.payments WHERE invoice_id = ?",
                row -> row.getBigDecimal("paid"), invoiceId).orElseThrow();
    }

    /**
     * Formats an amount as "150.00 SAR". Uses plain ASCII digits whatever the server's locale
     * (String.format could print Arabic-Indic digits on a machine set to Arabic).
     */
    private static String sar(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " SAR";
    }
}
