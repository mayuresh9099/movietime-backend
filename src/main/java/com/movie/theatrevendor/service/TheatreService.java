package com.movie.theatrevendor.service;

import com.movie.booking.service.ScreenRepository;
import com.movie.module.user.Role;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.OwnerRepository;
import com.movie.theatrevendor.repository.TheatreRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class TheatreService {

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // ✅ Constructor Injection
    public TheatreService(UserRepository userRepository,
                          OwnerRepository ownerRepository,
                          TheatreRepository theatreRepository, ScreenRepository screenRepository,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public String createTheatre(TheatreDetails theatre, String email) {

        System.out.println("🎬 Starting theatre creation for email: " + email);

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

        theatreRepository.flush();

        // ✅ VERIFY
        TheatreDetails verify = theatreRepository.findById(saved.getId())
                .orElseThrow(() -> new RuntimeException("Theatre save failed"));

        // ✅ 🔥 CREATE SCREENS HERE
        createScreensForTheatre(saved);

        System.out.println("✅ Theatre + Screens created: " + saved.getId());

        try {
            kafkaTemplate.send("theatre-events", "THEATRE_CREATED: " + saved.getId());
        } catch (Exception e) {
            System.out.println("⚠️ Kafka failed: " + e.getMessage());
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

    private void createScreensForTheatre(TheatreDetails theatre) {

        int totalScreens = theatre.getTotalScreens();

        if (theatre.getTotalScreens() == null || theatre.getTotalScreens() <= 0) {
            throw new RuntimeException("Invalid number of screens");
        }

        List<Screen> screens = IntStream.rangeClosed(1, theatre.getTotalScreens())
                .mapToObj(i -> Screen.builder()
                        .theatre(theatre)
                        .name("Screen " + i)
                        .totalSeats(
                                theatre.getType() == TheatreType.MULTIPLEX ? 150 : 100
                        ) // smarter default
                        .status(ScreenStatus.ACTIVE)
                        .type(ScreenType.STANDARD)
                        .build()
                )
                .toList();

        screenRepository.saveAll(screens);

        screenRepository.saveAll(screens);

        System.out.println("✅ " + totalScreens + " screens created for theatre " + theatre.getId());
    }
}