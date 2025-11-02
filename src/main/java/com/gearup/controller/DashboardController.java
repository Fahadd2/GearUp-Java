package com.gearup.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/kpis")
    public ResponseEntity<?> getKpis() {
        LocalDate today = LocalDate.now();

        Integer pickups = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) as count
            FROM public.reservations
            WHERE start_date = ?
              AND status = 'Reserved'
        """, Integer.class, today);

        Integer returns = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) as count
            FROM public.reservations
            WHERE end_date = ?
              AND status = 'Active'
        """, Integer.class, today);

        Integer active = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) as count
            FROM public.reservations
            WHERE status = 'Active'
        """, Integer.class);

        Integer unpaid = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) as count
            FROM public.invoices
            WHERE payment_status IN ('unpaid', 'partial')
        """, Integer.class);

        Map<String, Object> response = new HashMap<>();
        response.put("todays_pickups", pickups != null ? pickups : 0);
        response.put("todays_returns", returns != null ? returns : 0);
        response.put("active_rentals", active != null ? active : 0);
        response.put("unpaid_invoices", unpaid != null ? unpaid : 0);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenueStats() {
        Map<String, Object> stats = jdbcTemplate.queryForMap("""
            SELECT
                SUM(CASE WHEN payment_status = 'paid' THEN total_amount ELSE 0 END) as total_revenue,
                SUM(CASE WHEN payment_status IN ('unpaid', 'partial') THEN total_amount ELSE 0 END) as pending_revenue,
                SUM(CASE 
                    WHEN payment_status = 'paid' 
                    AND EXTRACT(MONTH FROM issue_date) = EXTRACT(MONTH FROM CURRENT_DATE)
                    AND EXTRACT(YEAR FROM issue_date) = EXTRACT(YEAR FROM CURRENT_DATE)
                    THEN total_amount 
                    ELSE 0 
                END) as this_month_revenue
            FROM public.invoices
        """);

        BigDecimal totalRevenue = (BigDecimal) stats.get("total_revenue");
        BigDecimal pendingRevenue = (BigDecimal) stats.get("pending_revenue");
        BigDecimal thisMonthRevenue = (BigDecimal) stats.get("this_month_revenue");

        Map<String, Object> response = new HashMap<>();
        response.put("total_revenue", totalRevenue != null ? totalRevenue.doubleValue() : 0.0);
        response.put("pending_revenue", pendingRevenue != null ? pendingRevenue.doubleValue() : 0.0);
        response.put("this_month_revenue", thisMonthRevenue != null ? thisMonthRevenue.doubleValue() : 0.0);

        return ResponseEntity.ok(response);
    }
}
