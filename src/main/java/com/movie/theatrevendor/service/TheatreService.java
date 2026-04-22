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

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getRole().equals(Role.THEATRE_OWNER)) {
            throw new RuntimeException("Only theatre owners can create theatre");
        }

        Owner owner = ownerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Owner profile not found"));

        if (theatreRepository.findByOwner(owner).isPresent()) {
            throw new RuntimeException("Theatre already exists");
        }

        validateTheatre(theatre);

        theatre.setOwner(owner);
        theatre.setStatus(TheatreStatus.PENDING);

        TheatreDetails saved = theatreRepository.save(theatre);

        // Kafka event
        kafkaTemplate.send("theatre-events", "THEATRE_CREATED: " + saved.getId());

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