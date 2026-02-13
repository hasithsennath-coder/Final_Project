package com.example.final_project.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    private String address;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private PropertyType type;

    @Enumerated(EnumType.STRING)
    private HouseType houseType;

    @Enumerated(EnumType.STRING)
    private PropertyStatus status;

    // Main thumbnail shown in cards.
    private String imageUrl;

    // All image URLs for gallery/detail views.
    @ElementCollection
    @CollectionTable(name = "property_images", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    private Integer bedrooms;
    private Integer bathrooms;
    private Double areaSqFt;

    @ElementCollection
    @CollectionTable(name = "property_facilities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "facility")
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String houseRules;

    @ManyToOne
    @JoinColumn(name = "agent_id")
    private User agent;

    @ManyToOne
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;
    private String driveLink;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<PropertyMedia> mediaFiles = new ArrayList<>();

    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String adminDecisionMessage;

    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
