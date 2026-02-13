package com.example.final_project.service;

import com.example.final_project.model.PropertyMedia;
import com.example.final_project.model.Property;
import com.example.final_project.model.PropertyStatus;
import com.example.final_project.model.PropertyType;
import com.example.final_project.model.HouseType;
import com.example.final_project.model.User;
import com.example.final_project.repository.PropertyMediaRepository;
import com.example.final_project.repository.PropertyRepository;
import com.example.final_project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PropertyMediaRepository propertyMediaRepository;
    private final NotificationService notificationService;

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

                // Keep owner fields in sync so /my-listings returns agent-created records too.
                if (property.getOwnerEmail() == null || property.getOwnerEmail().isBlank()) {
                    property.setOwnerEmail(username);
                }
                if ((property.getOwnerName() == null || property.getOwnerName().isBlank())
                        && agent != null
                        && agent.getName() != null
                        && !agent.getName().isBlank()) {
                    property.setOwnerName(agent.getName());
                }
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
    @Transactional
    public Property saveProperty(Property property, MultipartFile[] files) {
        // 1. Set Status to PENDING so it appears in Admin Dashboard
        if (property.getId() == null) {
            property.setStatus(PropertyStatus.PENDING);
            property.setCreatedAt(LocalDateTime.now());
        }

        // 2. Save the property first to get an ID
        Property savedProperty = propertyRepository.save(property);

        // 3. Handle Image Uploads
        if (files != null && files.length > 0) {
            if (savedProperty.getImageUrls() == null) {
                savedProperty.setImageUrls(new ArrayList<>());
            }
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    // Store the file to the 'uploads' folder
                    String filePath = fileStorageService.storeFile(file);

                    // Create a database record linking the photo to the property
                    PropertyMedia media = PropertyMedia.builder()
                            .property(savedProperty)
                            .filePath(filePath)
                            .build();

                    propertyMediaRepository.save(media);

                    // Set the first image as the main thumbnail if not set
                    if (savedProperty.getImageUrl() == null) {
                        savedProperty.setImageUrl(filePath);
                    }
                    savedProperty.getImageUrls().add(filePath);
                }
            }
            propertyRepository.save(savedProperty);
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
        existingProperty.setHouseType(updatedProperty.getHouseType());
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
    @Transactional
    public Property submitProperty(com.example.final_project.dto.PropertySubmissionDTO dto,
            MultipartFile[] files) {
        String driveLink = dto.getDriveLink() != null ? dto.getDriveLink().trim() : null;
        boolean hasDriveLink = driveLink != null && !driveLink.isBlank();
        boolean hasFiles = files != null && Arrays.stream(files).anyMatch(f -> f != null && !f.isEmpty());

        if (!hasFiles && !hasDriveLink) {
            throw new RuntimeException("Google Drive link is required when no files are uploaded.");
        }
        if (hasDriveLink && !isValidGoogleDriveLink(driveLink)) {
            throw new RuntimeException(
                    "Invalid Google Drive link. Please provide a valid drive.google.com/docs.google.com resource URL.");
        }

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
                .houseType(determineHouseType(dto.getHouseType()))
                .status(PropertyStatus.PENDING)
                .bedrooms(dto.getBedrooms())
                .bathrooms(dto.getBathrooms())
                .areaSqFt(dto.getAreaSqFt())
                .facilities(dto.getAmenities() != null ? dto.getAmenities() : new java.util.ArrayList<>())
                .ownerName(dto.getOwnerName())
                .ownerPhone(dto.getOwnerPhone())
                .ownerEmail(dto.getOwnerEmail())
                .driveLink(hasDriveLink ? driveLink : null)
                .agent(selectedAgent)
                .createdAt(LocalDateTime.now())
                .build();

        // Save property first to get ID
        property = propertyRepository.save(property);

        List<String> storedFilePaths = new ArrayList<>();

        // Store uploaded files and create PropertyMedia records
        try {
            if (files != null && files.length > 0) {
                if (property.getImageUrls() == null) {
                    property.setImageUrls(new ArrayList<>());
                }
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String filePath = fileStorageService.storeFile(file);
                        storedFilePaths.add(filePath);

                        PropertyMedia media = PropertyMedia.builder()
                                .property(property)
                                .filePath(filePath)
                                .build();
                        propertyMediaRepository.save(media);

                        property.getImageUrls().add(filePath);
                        if (property.getImageUrl() == null) {
                            property.setImageUrl(filePath);
                        }
                    }
                }
                property = propertyRepository.save(property);
            }
        } catch (RuntimeException ex) {
            cleanupStoredFiles(storedFilePaths);
            throw ex;
        }

        return property;
    }

    private PropertyType determinePropertyType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return PropertyType.SALE;
        }
        try {
            return PropertyType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PropertyType.SALE;
        }
    }

    private HouseType determineHouseType(String houseTypeStr) {
        if (houseTypeStr == null || houseTypeStr.isBlank()) {
            return null;
        }
        try {
            return HouseType.valueOf(houseTypeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
    @Transactional
    public Property approveProperty(Long id, String adminMessage) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));

        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new RuntimeException("Property is not in PENDING status");
        }

        property.setStatus(PropertyStatus.AVAILABLE);
        property.setAdminDecisionMessage(adminMessage);
        property.setReviewedAt(LocalDateTime.now());
        Property saved = propertyRepository.save(property);

        notificationService.publishListingDecision(
                saved.getOwnerEmail(),
                saved.getId(),
                adminMessage,
                "Approved");
        return saved;
    }

    /**
     * Reject a pending property - sets status to REJECTED with reason.
     */
    @Transactional
    public Property rejectProperty(Long id, String reason) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Property not found with id: " + id));

        if (property.getStatus() != PropertyStatus.PENDING) {
            throw new RuntimeException("Property is not in PENDING status");
        }

        property.setStatus(PropertyStatus.REJECTED);
        property.setRejectionReason(reason);
        property.setAdminDecisionMessage(reason);
        property.setReviewedAt(LocalDateTime.now());
        Property saved = propertyRepository.save(property);

        notificationService.publishListingDecision(
                saved.getOwnerEmail(),
                saved.getId(),
                reason,
                "Rejected");
        return saved;
    }

    /**
     * Get all properties submitted by a specific user (by owner email).
     */
    public List<Property> getPropertiesByOwnerEmail(String ownerEmail) {
        return propertyRepository.findByOwnerEmail(ownerEmail);
    }

    private boolean isValidGoogleDriveLink(String link) {
        try {
            URI uri = URI.create(link);
            if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery() == null ? "" : uri.getQuery();

            if ("drive.google.com".equals(host)) {
                return path.matches("^/file/d/[^/]+.*")
                        || path.matches("^/drive/folders/[^/]+.*")
                        || path.matches("^/drive/u/\\d+/folders/[^/]+.*")
                        || ("/open".equals(path) && query.contains("id="))
                        || ("/uc".equals(path) && query.contains("id="));
            }

            if ("docs.google.com".equals(host)) {
                return path.matches("^/(document|spreadsheets|presentation|forms)/d/[^/]+.*");
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanupStoredFiles(List<String> storedFilePaths) {
        for (String path : storedFilePaths) {
            try {
                fileStorageService.deleteFile(path.substring(path.lastIndexOf('/') + 1));
            } catch (Exception ignored) {
                // Keep rollback path best-effort; DB transaction will still revert
                // property/media records.
            }
        }
    }
}
