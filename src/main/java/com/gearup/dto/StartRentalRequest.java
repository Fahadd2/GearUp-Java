package com.gearup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StartRentalRequest {
    @NotBlank
    @Pattern(regexp = "^RES-\\d+$", message = "Invalid reservation ID format")
    private String reservationId;
}

