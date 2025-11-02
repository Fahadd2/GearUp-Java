package com.gearup.dto;

import lombok.Data;

@Data
public class CarUpdateRequest {
    private String brand;
    private String model;
    private Integer year;
    private String category;
    private String transmission;
    private Double pricePerDay;
    private String status;
}
