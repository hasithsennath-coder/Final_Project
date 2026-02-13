package com.example.final_project.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PropertySubmissionDTO {
    private String title;
    private String description;
    private String address;
    private BigDecimal price;
    private String type; // "SALE", "RENT", "SHORT_STAY_RENTAL"
    private String houseType; // "APARTMENT", "MANUFACTURED", "HOUSE", "TOWNHOME", "LAND", "MULTI_FAMILY"
    private Integer bedrooms;
    private Integer bathrooms;
    private Double areaSqFt;
    private List<String> amenities;

    // Owner/seller contact info
    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;
    private String driveLink;

    // Selected agent
    private String agentName;
}
