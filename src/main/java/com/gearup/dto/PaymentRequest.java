package com.gearup.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotBlank
    @Pattern(regexp = "^INV-\\d+$", message = "Invalid invoice ID format")
    private String invoiceId;

    @NotBlank
    @Pattern(regexp = "^(cash|card|transfer)$", message = "Method must be cash, card, or transfer")
    private String method;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String reference;
}
