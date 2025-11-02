package com.gearup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseRentalRequest {
    @NotBlank
    @Pattern(regexp = "^RES-\\d+$", message = "Invalid reservation ID format")
    private String reservationId;

    private BigDecimal damageFee;
    
    private BigDecimal refuelFee;
}
