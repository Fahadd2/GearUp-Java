package com.gearup.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/invoices")
public class InvoicesController {

    private final JdbcTemplate jdbcTemplate;

    public InvoicesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listInvoices(
            @RequestParam(defaultValue = "100") int limit
    ) {
        int lim = Math.max(1, Math.min(limit, 200));

        List<Map<String, Object>> invoices = jdbcTemplate.queryForList("""
            SELECT
                i.inv_id,
                i.reservation_id,
                i.issue_date,
                i.total_amount,
                i.payment_status,
                i.created_at,
                r.customer_license_no,
                r.car_id,
                r.start_date,
                r.end_date
            FROM public.invoices i
            JOIN public.reservations r ON r.res_id = i.reservation_id
            ORDER BY i.created_at DESC
            LIMIT ?
        """, lim);

        return ResponseEntity.ok(invoices);
    }
}
