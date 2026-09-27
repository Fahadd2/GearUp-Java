package com.gearup.service;

import com.gearup.auth.CustomerUser;
import com.gearup.auth.PasswordHasher;
import com.gearup.auth.StaffUser;
import com.gearup.auth.TokenService;
import com.gearup.db.Database;
import com.gearup.db.DatabaseException;
import com.gearup.exception.BadRequestException;
import com.gearup.exception.ConflictException;
import com.gearup.exception.UnauthorizedException;
import com.gearup.model.Customer;
import com.gearup.model.CustomerSession;
import com.gearup.model.Employee;
import com.gearup.model.LabeledEnum;
import com.gearup.model.LoginRequest;
import com.gearup.model.ResetPasswordRequest;
import com.gearup.model.SignUpRequest;
import com.gearup.model.StaffLoginRequest;
import com.gearup.model.StaffRole;
import com.gearup.model.StaffSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;

/**
 * Sign-up, login and password reset for customers, and login for staff.
 *
 * <p>Password hashing is slow on purpose (bcrypt), so it is always done before opening a
 * database transaction; that way no connection is held while the CPU is busy hashing.
 */
public final class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MIN_AGE = 17;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 100;

    private final Database database;
    private final PasswordHasher passwords;
    private final TokenService tokens;

    public AuthService(Database database, PasswordHasher passwords, TokenService tokens) {
        this.database = database;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    /** Stored login data for one account: the public profile plus the password hash. */
    private record Account<T>(T profile, String passwordHash) {
    }

    // ------------------------------------------------------------------ staff

    public StaffSession staffLogin(StaffLoginRequest request) {
        String email = Validation.requireEmail(request.email()).toLowerCase(Locale.ROOT);
        String password = Validation.requireValue(request.password(), "password");
        StaffRole role = Validation.requireValue(request.role(), "role");

        Account<Employee> account = database.inTransaction(tx -> tx.queryOne("""
                        SELECT emp_id, email, lower(role) AS role, first_name, last_name,
                               COALESCE(password_hash, '') AS password_hash
                        FROM public.employees
                        WHERE lower(email) = ? AND lower(role) = ?
                        LIMIT 1""",
                row -> new Account<>(
                        new Employee(row.getString("emp_id"), row.getString("email"),
                                row.getString("first_name"), row.getString("last_name"),
                                LabeledEnum.fromLabel(StaffRole.class, row.getString("role"))),
                        row.getString("password_hash")),
                email, role.label()))
                .filter(found -> passwords.matches(password, found.passwordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials or role"));

        Employee employee = account.profile();
        String token = tokens.issue(new StaffUser(employee.id(), employee.email(), employee.role()));
        log.info("Staff member {} logged in as {}", employee.id(), employee.role().label());
        return new StaffSession(token, employee);
    }

    // --------------------------------------------------------------- customers

    public CustomerSession signUp(SignUpRequest request) {
        String firstName = Validation.requireLength(request.firstName(), "first_name", 1, MAX_NAME_LENGTH);
        String lastName = Validation.requireLength(request.lastName(), "last_name", 1, MAX_NAME_LENGTH);
        String email = Validation.requireEmail(request.email());
        String licenseNo = Validation.requireText(request.licenseNo(), "license_no");
        LocalDate licenseExpiry = Validation.requireValue(request.licenseExpiry(), "license_expiry");
        LocalDate dateOfBirth = Validation.requireValue(request.dateOfBirth(), "date_of_birth");
        String password = Validation.requireLength(request.password(), "password",
                MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);

        LocalDate today = LocalDate.now();
        if (Period.between(dateOfBirth, today).getYears() < MIN_AGE) {
            throw new BadRequestException("You must be at least " + MIN_AGE + " years old to sign up.");
        }
        if (!licenseExpiry.isAfter(today)) {
            throw new BadRequestException("License expiry must be a future date.");
        }

        String passwordHash = passwords.hash(password);
        Customer customer;
        try {
            customer = database.inTransaction(tx -> {
                if (tx.queryOne("SELECT 1 FROM public.customers WHERE lower(email) = lower(?)",
                        row -> true, email).isPresent()) {
                    throw new ConflictException("Email already registered");
                }
                if (tx.queryOne("SELECT 1 FROM public.customers WHERE license_no = ?",
                        row -> true, licenseNo).isPresent()) {
                    throw new ConflictException("License number already registered");
                }
                return tx.queryOne("""
                                INSERT INTO public.customers
                                    (license_no, first_name, last_name, email, phone,
                                     license_expiry, date_of_birth, password_hash)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                RETURNING license_no, first_name, last_name, email""",
                        AuthService::toCustomer,
                        licenseNo, firstName, lastName, email, request.phone(),
                        licenseExpiry, dateOfBirth, passwordHash).orElseThrow();
            });
        } catch (DatabaseException e) {
            // Another request registered the same email or license between our check and our insert.
            if (e.isUniqueViolation()) {
                throw new ConflictException("Email or license number already registered");
            }
            throw e;
        }

        log.info("New customer signed up with license {}", customer.licenseNo());
        return new CustomerSession(issueCustomerToken(customer), customer);
    }

    public CustomerSession logIn(LoginRequest request) {
        String email = Validation.requireEmail(request.email());
        String password = Validation.requireValue(request.password(), "password");

        Account<Customer> account = database.inTransaction(tx -> tx.queryOne("""
                        SELECT license_no, first_name, last_name, email,
                               COALESCE(password_hash, '') AS password_hash
                        FROM public.customers
                        WHERE lower(email) = lower(?)
                        LIMIT 1""",
                row -> new Account<>(toCustomer(row), row.getString("password_hash")),
                email))
                .filter(found -> passwords.matches(password, found.passwordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        return new CustomerSession(issueCustomerToken(account.profile()), account.profile());
    }

    /** Lets a customer set a new password by proving they know their email and license number. */
    public void resetPassword(ResetPasswordRequest request) {
        String email = Validation.requireEmail(request.email());
        String licenseNo = Validation.requireText(request.licenseNo(), "license_no");
        String newPassword = Validation.requireLength(request.newPassword(), "new_password",
                MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);

        String newHash = passwords.hash(newPassword);
        database.inTransaction(tx -> {
            String storedLicense = tx.queryOne(
                            "SELECT license_no FROM public.customers WHERE lower(email) = lower(?) LIMIT 1",
                            row -> row.getString("license_no"), email)
                    .filter(found -> normalizeLicense(found).equals(normalizeLicense(licenseNo)))
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or license number"));

            tx.update("UPDATE public.customers SET password_hash = ? WHERE license_no = ?", newHash, storedLicense);
            return null;
        });
        log.info("Password reset for customer with email {}", email);
    }

    // ----------------------------------------------------------------- helpers

    private String issueCustomerToken(Customer customer) {
        return tokens.issue(new CustomerUser(customer.licenseNo(), customer.email()));
    }

    private static Customer toCustomer(ResultSet row) throws SQLException {
        return new Customer(row.getString("license_no"), row.getString("first_name"),
                row.getString("last_name"), row.getString("email"));
    }

    /** "ab-12 34" and "AB1234" are the same license: keep letters and digits, upper-case them. */
    private static String normalizeLicense(String license) {
        StringBuilder normalized = new StringBuilder();
        license.strip().codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toUpperCase)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
