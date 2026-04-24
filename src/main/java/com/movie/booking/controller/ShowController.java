package com.movie.booking.controller;

import com.movie.booking.service.ScreenRepository;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.CreateShowRequest;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final TheatreRepository theatreRepository;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final ScreenRepository screenRepository;
    private final ShowSeatRepository showSeatRepository;

    // ========================= CREATE SHOW =========================

    @PostMapping("/create")
    public ResponseEntity<?> createShow(@RequestBody CreateShowRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            if (request.getScreenId() == null) {
                return ResponseEntity.badRequest().body("screenId is required");
            }

            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Owner owner = ownerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("You are not an owner"));

            TheatreDetails theatre = theatreRepository.findById(request.getTheatreId())
                    .orElseThrow(() -> new RuntimeException("Theatre not found"));

            if (!theatre.getOwner().getId().equals(owner.getId())) {
                return ResponseEntity.status(403).body("Unauthorized");
            }

            if (theatre.getStatus() != TheatreStatus.APPROVED) {
                return ResponseEntity.badRequest().body("Theatre not approved");
            }

            Screen screen = screenRepository
                    .findByIdAndTheatreId(request.getScreenId(), request.getTheatreId())
                    .orElseThrow(() -> new RuntimeException("Screen not found for this theatre"));

            if (request.getStartTime().isAfter(request.getEndTime())) {
                return ResponseEntity.badRequest().body("Invalid time");
            }
// ✅ Validate shows can span at most 2 calendar days (allows midnight shows like 9 PM → 12:30 AM)
            if (request.getEndTime().toLocalDate().isAfter(request.getStartTime().toLocalDate().plusDays(1))) {
                throw new RuntimeException("Show cannot span more than 2 calendar days");
            }

            // ✅ Validate max show duration (4 hours) - prevents unrealistic overnight shows
            if (java.time.temporal.ChronoUnit.MINUTES.between(request.getStartTime(), request.getEndTime()) > 240) {
                throw new RuntimeException("Show duration cannot exceed 4 hours");
            }
            boolean exists = showRepository.existsByScreenAndTimeOverlap(
                    screen.getId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            if (exists) {
                return ResponseEntity.badRequest().body("Show overlap");
            }

            Show show = Show.builder()
                    .theatre(theatre)
                    .screen(screen)
                    .movieName(request.getMovieName())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .totalSeats(request.getTotalSeats())
                    .availableSeats(request.getTotalSeats())
                    .pricePerSeat(request.getPricePerSeat())
                    .status(ShowStatus.ACTIVE)
                    .build();

            Show savedShow = showRepository.save(show);

            // ✅ Create seats only once per screen
            if (seatRepository.countByScreen(screen) == 0) {
                if (screen.getTotalSeats() == null || screen.getTotalSeats() == 0) {
                    throw new RuntimeException("Screen has no seats configured");
                }
                createSeatsForScreen(screen);
            }

            // ✅ CRITICAL: Create ShowSeat mapping
            createShowSeats(savedShow);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "showId", savedShow.getId()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ========================= AVAILABLE SEATS =========================

    @GetMapping("/{showId}/available-seats")
    public ResponseEntity<?> getAvailableSeats(@PathVariable Long showId) {

        List<ShowSeat> seats = showSeatRepository.findAvailableSeats(showId);

        List<String> seatNumbers = seats.stream()
                .map(ss -> ss.getSeat().getSeatNumber())
                .toList();

        return ResponseEntity.ok(Map.of(
                "showId", showId,
                "availableSeats", seatNumbers.size(),
                "seats", seatNumbers
        ));
    }

    // ========================= SHOW DETAILS =========================

    @GetMapping("/{showId}")
    public ResponseEntity<?> getShowDetails(@PathVariable Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        Integer availableSeats = showSeatRepository.countAvailableSeats(showId);

        return ResponseEntity.ok(Map.of(
                "id", show.getId(),
                "movie", show.getMovieName(),
                "availableSeats", availableSeats
        ));
    }

    // ========================= DEBUG =========================

    @GetMapping("/debug/{showId}")
    public ResponseEntity<?> debugShow(@PathVariable Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        List<ShowSeat> allShowSeats = showSeatRepository.findAvailableSeats(showId);
        Integer availableCount = showSeatRepository.countAvailableSeats(showId);
        List<Object[]> summary = showSeatRepository.getShowSeatSummary(showId);

        return ResponseEntity.ok(Map.of(
                "show", Map.of(
                        "id", show.getId(),
                        "movieName", show.getMovieName()
                ),
                "stats", Map.of(
                        "total", allShowSeats.size(),
                        "available", availableCount
                ),
                "sample", allShowSeats.stream().limit(5).map(ss ->
                        Map.of(
                                "seat", ss.getSeat().getSeatNumber(),
                                "status", ss.getStatus()
                        )
                ).toList()
        ));
    }

    // ========================= HELPERS =========================

   /* private void createSeatsForScreen(Screen screen, Integer totalSeats) {

        int seatsPerRow = 10;
        int count = 0;

        for (int i = 0; count < totalSeats; i++) {
            char row = (char) ('A' + i);

            for (int j = 1; j <= seatsPerRow && count < totalSeats; j++) {
                Seat seat = new Seat();
                seat.setScreen(screen);
                seat.setSeatNumber(row + String.valueOf(j));
                seatRepository.save(seat);
                count++;
            }
        }

        log.info("Seats created for screen {}", screen.getId());
    }*/

    private void createSeatsForScreen(Screen screen) {

        List<Seat> seats = new java.util.ArrayList<>();

        int totalSeats = screen.getTotalSeats();

        char row = 'A';
        int seatNumber = 1;

        for (int i = 0; i < totalSeats; i++) {

            String seatLabel = row + String.valueOf(seatNumber);

            seats.add(Seat.builder()
                    .screen(screen)
                    .seatNumber(seatLabel)
                    .build());

            seatNumber++;

            if (seatNumber > 10) {  // 10 seats per row
                seatNumber = 1;
                row++;
            }
        }

        seatRepository.saveAll(seats);

        log.info("✅ Created {} seats for screen {}", seats.size(), screen.getId());
    }

    private void createShowSeats(Show show) {

        List<Seat> seats = seatRepository.findByScreen(show.getScreen());

        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(SeatStatus.AVAILABLE)
                        .build())
                .toList();

        showSeatRepository.saveAll(showSeats);

        log.info("ShowSeats created for show {}", show.getId());
    }
}