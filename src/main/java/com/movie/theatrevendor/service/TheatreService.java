package com.movie.theatrevendor.service;

import com.movie.module.user.Role;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.OwnerRepository;
import com.movie.theatrevendor.repository.TheatreRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TheatreService {

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final TheatreRepository theatreRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // ✅ Constructor Injection
    public TheatreService(UserRepository userRepository,
                          OwnerRepository ownerRepository,
                          TheatreRepository theatreRepository,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.theatreRepository = theatreRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public String createTheatre(TheatreDetails theatre, String email) {

        // Debug: Log method entry
        System.out.println("🎬 Starting theatre creation for email: " + email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getRole().equals(Role.THEATRE_OWNER)) {
            throw new RuntimeException("Only theatre owners can create theatre");
        }

        Owner owner = ownerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Owner profile not found"));

        System.out.println("✅ Found owner: " + owner.getBusinessName() + ", verified: " + owner.getIsVerified());

        if (theatreRepository.findByOwner(owner).isPresent()) {
            throw new RuntimeException("Theatre already exists");
        }

        validateTheatre(theatre);

        theatre.setOwner(owner);
        theatre.setStatus(TheatreStatus.PENDING);

        System.out.println("🔄 About to save theatre: " + theatre.getName());

        TheatreDetails saved = theatreRepository.save(theatre);

        System.out.println("✅ Theatre saved with ID: " + saved.getId());

        // Force flush to ensure it's in database
        theatreRepository.flush();

        // Verify immediately
        TheatreDetails verify = theatreRepository.findById(saved.getId()).orElse(null);
        if (verify == null) {
            throw new RuntimeException("CRITICAL: Theatre save failed - not found after save!");
        }

        System.out.println("✅ Theatre verified in database: " + verify.getName());

        // Kafka event (optional)
        try {
            kafkaTemplate.send("theatre-events", "THEATRE_CREATED: " + saved.getId());
        } catch (Exception e) {
            System.out.println("⚠️ Kafka event failed, but theatre was saved: " + e.getMessage());
        }

        return "Theatre submitted for approval";
    }

    private void validateTheatre(TheatreDetails theatre) {

        if (theatre.getName() == null || theatre.getName().isBlank()) {
            throw new RuntimeException("Theatre name is required");
        }

        if (theatre.getCity() == null || theatre.getCity().isBlank()) {
            throw new RuntimeException("City is required");
        }

        if (theatre.getAddress() == null || theatre.getAddress().isBlank()) {
            throw new RuntimeException("Address is required");
        }

        if (theatre.getType() == TheatreType.MULTIPLEX &&
                (theatre.getTotalScreens() == null || theatre.getTotalScreens() <= 1)) {
            throw new RuntimeException("Multiplex must have more than 1 screen");
        }

        if (theatre.getType() != TheatreType.MULTIPLEX) {
            theatre.setTotalScreens(1);
        }

        if (theatre.getContactNumber() != null &&
                theatre.getContactNumber().length() < 10) {
            throw new RuntimeException("Invalid contact number");
        }
    }

    public Owner createOwner(OwnerRequest request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() != Role.THEATRE_OWNER) {
            throw new RuntimeException("User is not authorized to be an owner");
        }

        // prevent duplicate owner for same user
        ownerRepository.findByUserId(request.getUserId())
                .ifPresent(o -> {
                    throw new RuntimeException("Owner already exists for this user");
                });

        Owner owner = new Owner();
        owner.setUser(user);
        owner.setBusinessName(request.getBusinessName());
        owner.setIsVerified(request.getIsVerified() != null ? request.getIsVerified() : false);

        return ownerRepository.save(owner);
    }
}