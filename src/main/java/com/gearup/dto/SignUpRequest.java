package com.gearup.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SignUpRequest {
    @NotBlank
    @Size(min = 1, max = 100)
    @JsonProperty("first_name")
    private String firstName;

    @NotBlank
    @Size(min = 1, max = 100)
    @JsonProperty("last_name")
    private String lastName;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @NotBlank
    @JsonProperty("license_no")
    private String licenseNo;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
    @JsonProperty("license_expiry")
    private String licenseExpiry;

    @NotBlank
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in YYYY-MM-DD format")
    @JsonProperty("date_of_birth")
    private String dateOfBirth;

    @NotBlank
    @Size(min = 6, max = 128)
    private String password;
}