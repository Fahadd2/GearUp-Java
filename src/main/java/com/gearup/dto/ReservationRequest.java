package com.gearup.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ReservationRequest {
    @NotBlank
    @JsonProperty("car_id")
    private String carId;

    @NotNull
    @JsonProperty("start_date")
    private LocalDate startDate;

    @NotNull
    @JsonProperty("end_date")
    private LocalDate endDate;
}