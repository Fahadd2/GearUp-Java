package com.gearup.controller;

import com.gearup.dto.*;
import com.gearup.security.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;

    public AuthController(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/staff_login")
    public ResponseEntity<?> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String role = request.getRole().toLowerCase().trim();

        String sql = """
            SELECT emp_id, email, LOWER(role) as role,
                   COALESCE(password_hash,'') as password_hash,
                   first_name, last_name
            FROM public.employees
            WHERE LOWER(email) = ?
              AND LOWER(role) = ?
            LIMIT 1
        """;

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, email, role);
            String passwordHash = (String) row.get("password_hash");

            if (passwordHash.isEmpty() || !BCrypt.checkpw(request.getPassword(), passwordHash)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or role");
            }

            String token = jwtUtil.generateToken((String) row.get("emp_id"), (String) row.get("email"));

            Map<String, Object> employee = new HashMap<>();
            employee.put("id", row.get("emp_id"));
            employee.put("email", row.get("email"));
            employee.put("first_name", row.get("first_name"));
            employee.put("last_name", row.get("last_name"));
            employee.put("role", row.get("role"));

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("employee", employee);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or role");
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest request) {
        try {
            LocalDate dob = LocalDate.parse(request.getDateOfBirth());
            LocalDate licenseExp = LocalDate.parse(request.getLicenseExpiry());
            LocalDate today = LocalDate.now();

            int age = Period.between(dob, today).getYears();
            if (age < 17) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "You must be at least 17 years old to sign up.");
            }

            if (licenseExp.isBefore(today) || licenseExp.isEqual(today)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "License expiry must be a future date.");
            }

            Integer emailExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.customers WHERE LOWER(email) = ?",
                Integer.class, request.getEmail().toLowerCase()
            );
            if (emailExists > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }

            Integer licenseExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.customers WHERE license_no = ?",
                Integer.class, request.getLicenseNo()
            );
            if (licenseExists > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "License number already registered");
            }

            String passwordHash = BCrypt.hashpw(request.getPassword(), BCrypt.gensalt());

            String insertSql = """
                INSERT INTO public.customers (
                    license_no, first_name, last_name, email, phone,
                    license_expiry, date_of_birth, address_street, address_city, password_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)
                RETURNING license_no, first_name, last_name, email
            """;

            Map<String, Object> customer = jdbcTemplate.queryForMap(insertSql,
                request.getLicenseNo(), request.getFirstName(), request.getLastName(),
                request.getEmail(), request.getPhone(), licenseExp, dob, passwordHash
            );

            String token = jwtUtil.generateToken(
                (String) customer.get("license_no"),
                (String) customer.get("email")
            );

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("customer", customer);

            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        String sql = """
            SELECT license_no, email, COALESCE(password_hash,'') as password_hash,
                   first_name, last_name
            FROM public.customers
            WHERE LOWER(email) = ?
            LIMIT 1
        """;

        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(sql, request.getEmail().toLowerCase());
            String passwordHash = (String) user.get("password_hash");

            if (passwordHash.isEmpty() || !BCrypt.checkpw(request.getPassword(), passwordHash)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
            }

            String token = jwtUtil.generateToken(
                (String) user.get("license_no"),
                (String) user.get("email")
            );

            Map<String, Object> customer = new HashMap<>();
            customer.put("license_no", user.get("license_no"));
            customer.put("email", user.get("email"));
            customer.put("first_name", user.get("first_name"));
            customer.put("last_name", user.get("last_name"));

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("customer", customer);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
    }

    @PostMapping("/reset_by_license")
    public ResponseEntity<?> resetByLicense(@Valid @RequestBody ResetPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        String licenseIn = normalizeLicense(request.getLicenseNo());

        String sql = """
            SELECT license_no, email
            FROM public.customers
            WHERE LOWER(email) = ?
            LIMIT 1
        """;

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, email);
            String storedLicense = normalizeLicense((String) row.get("license_no"));

            if (!storedLicense.equals(licenseIn)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                    "Invalid email or license number");
            }

            String newHash = BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt());
            jdbcTemplate.update(
                "UPDATE public.customers SET password_hash = ? WHERE license_no = ?",
                newHash, row.get("license_no")
            );

            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("message", "Password has been reset");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or license number");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String sub = (String) authentication.getPrincipal();
        
        Map<String, Object> response = new HashMap<>();
        response.put("sub", sub);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        return ResponseEntity.ok(response);
    }

    private String normalizeLicense(String license) {
        return license.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
    }
}
