package com.gearup.controller;

import com.gearup.dto.PaymentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentsController {

    private final JdbcTemplate jdbcTemplate;

    public PaymentsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/pay")
    public ResponseEntity<?> recordPayment(@Valid @RequestBody PaymentRequest request) {
        String invoiceId = request.getInvoiceId();
        BigDecimal amount = request.getAmount();
        String method = request.getMethod();
        String reference = request.getReference();

        Map<String, Object> invoice = jdbcTemplate.queryForMap("""
            SELECT inv_id, total_amount, payment_status
            FROM public.invoices
            WHERE inv_id = ?
        """, invoiceId);

        if (invoice == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }

        jdbcTemplate.update("""
            INSERT INTO public.payments (invoice_id, method, amount, reference)
            VALUES (?, ?::payment_method, ?, ?)
        """, invoiceId, method, amount, reference);

        BigDecimal paid = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(amount), 0) AS paid
            FROM public.payments
            WHERE invoice_id = ?
        """, BigDecimal.class, invoiceId);

        BigDecimal totalAmount = (BigDecimal) invoice.get("total_amount");
        String newStatus;
        if (paid.compareTo(totalAmount) >= 0) {
            newStatus = "paid";
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            newStatus = "partial";
        } else {
            newStatus = "unpaid";
        }

        jdbcTemplate.update("""
            UPDATE public.invoices
            SET payment_status = ?::payment_status
            WHERE inv_id = ?
        """, newStatus, invoiceId);

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("invoice_id", invoiceId);
        response.put("status", newStatus);
        response.put("paid_total", paid);

        return ResponseEntity.ok(response);
    }
}
