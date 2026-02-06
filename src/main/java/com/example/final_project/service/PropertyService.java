package com.example.final_project.service;

import com.example.final_project.model.Property;
import com.example.final_project.model.PropertyStatus;
import com.example.final_project.model.PropertyType;
import com.example.final_project.model.User;
import com.example.final_project.repository.PropertyRepository;
import com.example.final_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final com.example.final_project.repository.PropertyMediaRepository propertyMediaRepository;

    public List<Property> getAllProperties() {
        return propertyRepository.findAll();
    }

    public List<Property> searchProperties(String query) {
        // Search by title or address
        return propertyRepository.findByTitleContainingIgnoreCaseOrAddressContainingIgnoreCase(query, query);
    }

    public List<Property> getPropertiesByType(PropertyType type) {
        return propertyRepository.findByType(type);
    }

    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new RuntimeException("Property not found"));
    }

    public Property createProperty(Property property) {
        // Try to get current logged in user (Agent) - but make it optional
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                User agent = userRepository.findByEmail(username).orElse(null);
                property.setAgent(agent);
            }
        } catch (Exception e) {
            // No authenticated user - that's okay for admin panel
            property.setAgent(null);
        }

        property.setCreatedAt(LocalDateTime.now());
        if (property.getStatus() == null)
            property.setStatus(PropertyStatus.AVAILABLE);

        return propertyRepository.save(property);
    }


    // --- ADD THIS NEW METHOD ---
    public Property saveProperty(Property property, org.springframework.web.multipart.MultipartFile[] files) {
        // 1. Set Status to PENDING so it appears in Admin Dashboard
        if (property.getId() == null) {
            property.setStatus(PropertyStatus.PENDING);
            property.setCreatedAt(LocalDateTime.now());
        }

        // 2. Save the property first to get an ID
        Property savedProperty = propertyRepository.save(property);

        // 3. Handle Image Uploads
        if (files != null && files.length > 0) {
            for (org.springframework.web.multipart.MultipartFile file : files) {
                if (!file.isEmpty()) {
                    // Store the file to the 'uploads' folder
                    String filePath = fileStorageService.storeFile(file);

                    // Create a database record linking the photo to the property
                    com.example.final_project.model.PropertyMedia media = com.example.final_project.model.PropertyMedia.builder()
                            .property(savedProperty)
                            .filePath(filePath)
                            .build();

                    propertyMediaRepository.save(media);

                    // Set the first image as the main thumbnail if not set
                    if (savedProperty.getImageUrl() == null) {
                        savedProperty.setImageUrl(filePath);
                        propertyRepository.save(savedProperty);
                    }
                }
            }
        }
        return savedProperty;
    }


    public Property updateProperty(Long id, Property updatedProperty) {
        Property existingProperty = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));

        // Update fields
        existingProperty.setTitle(updatedProperty.getTitle());
        existingProperty.setDescription(updatedProperty.getDescription());
        existingProperty.setAddress(updatedProperty.getAddress());
        existingProperty.setPrice(updatedProperty.getPrice());
        existingProperty.setType(updatedProperty.getType());
        existingProperty.setStatus(updatedProperty.getStatus());
        existingProperty.setImageUrl(updatedProperty.getImageUrl());
        existingProperty.setBedrooms(updatedProperty.getBedrooms());
        existingProperty.setBathrooms(updatedProperty.getBathrooms());
        existingProperty.setAreaSqFt(updatedProperty.getAreaSqFt());
        existingProperty.setAssignedAgent(updatedProperty.getAssignedAgent());

        return propertyRepository.save(existingProperty);
    }

    public void deleteProperty(Long id) {
        propertyRepository.deleteById(id);
    }

    /**
     * Submit a property from the public form with file uploads.
     * Property status is set to PENDING for admin review.
     */
    public Property submitProperty(com.example.final_project.dto.PropertySubmissionDTO dto,
            org.springframework.web.multipart.MultipartFile[] files) {
        // Find agent by name if provided
        User selectedAgent = null;
        if (dto.getAgentName() != null && !dto.getAgentName().isEmpty()) {
            selectedAgent = userRepository.findAll().stream()
                    .filter(u -> u.getName().equals(dto.getAgentName()))
                    .findFirst()
                    .orElse(null);
        }

        // Build property entity
        Property property = Property.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .address(dto.getAddress())
                .price(dto.getPrice())
                .type(determinePropertyType(dto.getType()))
                .status(PropertyStatus.PENDING)
                .bedrooms(dto.getBedrooms())
                .bathrooms(dto.getBathrooms())
                .areaSqFt(dto.getAreaSqFt())
                .facilities(dto.getAmenities() != null ? dto.getAmenities() : new java.util.ArrayList<>())
                .ownerName(dto.getOwnerName())
                .ownerPhone(dto.getOwnerPhone())
                .ownerEmail(dto.getOwnerEmail())
                .agent(selectedAgent)
                .createdAt(LocalDateTime.now())
                .build();

        // Save property first to get ID
        property = propertyRepository.save(property);

        // Store uploaded files and create PropertyMedia records
        if (files != null && files.length > 0) {
            for (org.springframework.web.multipart.MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String filePath = fileStorageService.storeFile(file);

                    com.example.final_project.model.PropertyMedia media = com.example.final_project.model.PropertyMedia
                            .builder()
                            .property(property)
                            .filePath(filePath)
                            .build();

                    propertyMediaRepository.save(media);

                    // Set first image as main imageUrl
                    if (property.getImageUrl() == null) {
                        property.setImageUrl(filePath);
                    }
                }
            }

            // Update property with main image
            property = propertyRepository.save(property);
        }

        return property;
    }

    private PropertyType determinePropertyType(String typeStr) {
        // Map form values like "HOUSE", "APARTMENT" to SALE/RENT
        // Since the form doesn't specify SALE vs RENT, default to SALE
        // This can be enhanced later with a form field
        return PropertyType.SALE;
    }

    /**
     * Get all properties with PENDING status for admin review.
     */
    public List<Property> getPendingProperties() {
        return propertyRepository.findByStatus(PropertyStatus.PENDING);
    }

    /**
     * Approve a pending property - changes status to AVAILABLE.
     */
    public Property approveProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));

        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new RuntimeException("Property is not in PENDING status");
        }

        property.setStatus(PropertyStatus.AVAILABLE);
        return propertyRepository.save(property);
    }

    /**
     * Reject a pending property - sets status to REJECTED with reason.
     */
    public Property rejectProperty(Long id, String reason) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));

        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new RuntimeException("Property is not in PENDING status");
        }

        property.setStatus(PropertyStatus.REJECTED);
        property.setRejectionReason(reason);
        return propertyRepository.save(property);
    }

    /**
     * Get all properties submitted by a specific user (by owner email).
     */
    public List<Property> getPropertiesByOwnerEmail(String ownerEmail) {
        return propertyRepository.findByOwnerEmail(ownerEmail);
    }
}
