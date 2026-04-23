package com.movie.booking.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.movie.common.util.model.SeatStatus;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.CreateShowRequest;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.OwnerRepository;
import com.movie.theatrevendor.repository.ShowRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Create a new show
     * POST /api/shows/create
     *
     * Request Body:
     * {
     *   "theatreId": 1,
     *   "movieName": "Inception",
     *   "startTime": "2026-04-25T19:00:00",
     *   "endTime": "2026-04-25T22:00:00",
     *   "totalSeats": 50,
     *   "pricePerSeat": 250.0
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createShow(@RequestBody CreateShowRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            if (request.getScreenId() == null) {
                return ResponseEntity.badRequest()
                        .body("screenId is required");
            }
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Owner owner = ownerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("You are not an owner"));

            TheatreDetails theatre = theatreRepository.findById(request.getTheatreId())
                    .orElseThrow(() -> new RuntimeException("Theatre not found"));

            if (!theatre.getOwner().getId().equals(owner.getId())) {
                return ResponseEntity.status(403)
                        .body("You are not allowed to create shows for this theatre");
            }

            if (theatre.getStatus() != TheatreStatus.APPROVED) {
                return ResponseEntity.badRequest().body("Theatre is not approved");
            }

            // ✅ time validation
            if (request.getStartTime().isAfter(request.getEndTime())) {
                return ResponseEntity.badRequest()
                        .body("Start time must be before end time");
            }

            // ✅ overlap check
            boolean exists = showRepository.existsByScreenIdAndTimeOverlap(
                    request.getScreenId(),
                    request.getStartTime(),
                    request.getEndTime()
            );

            if (exists) {
                return ResponseEntity.badRequest()
                        .body("Show timing overlaps on this screen");
            }

            Show show = Show.builder()
                    .theatre(theatre)
                    .screenId(request.getScreenId())
                    .movieName(request.getMovieName())
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .totalSeats(request.getTotalSeats())
                    .availableSeats(request.getTotalSeats())
                    .pricePerSeat(request.getPricePerSeat())
                    .status(ShowStatus.ACTIVE)
                    .build();

            // Save show first
            Show savedShow = showRepository.save(show);
            log.info("✅ Show saved with ID: {}", savedShow.getId());

            // Create seats - simple loop
            createSeats(savedShow, request.getTotalSeats());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Show created successfully",
                    "showId", savedShow.getId()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get available seats for a show
     * GET /api/shows/{showId}/available-seats
     */
    @GetMapping("/{showId}/available-seats")
    public ResponseEntity<?> getAvailableSeats(@PathVariable Long showId) {
        try {
            Show show = showRepository.findById(showId)
                    .orElseThrow(() -> new RuntimeException("Show not found"));

            List<Seat> availableSeats = seatRepository.findByShowAndStatus(show, "AVAILABLE");
            Integer availableCount = seatRepository.countAvailableSeats(show);

            List<String> seatNumbers = availableSeats.stream()
                    .map(Seat::getSeatNumber)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "showId", showId,
                    "movieName", show.getMovieName(),
                    "totalSeats", show.getTotalSeats(),
                    "availableSeats", availableCount,
                    "pricePerSeat", show.getPricePerSeat(),
                    "availableSeatNumbers", seatNumbers
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Get show details
     * GET /api/shows/{showId}
     */
    @GetMapping("/{showId}")
    public ResponseEntity<?> getShowDetails(@PathVariable Long showId) {
        try {
            Show show = showRepository.findById(showId)
                    .orElseThrow(() -> new RuntimeException("Show not found"));

            Integer availableSeats = seatRepository.countAvailableSeats(show);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "show", Map.of(
                            "id", show.getId(),
                            "movieName", show.getMovieName(),
                            "theatre", show.getTheatre().getName(),
                            "startTime", show.getStartTime(),
                            "endTime", show.getEndTime(),
                            "totalSeats", show.getTotalSeats(),
                            "availableSeats", availableSeats,
                            "pricePerSeat", show.getPricePerSeat(),
                            "status", show.getStatus()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Get all shows for a theatre
     * GET /api/shows/theatre/{theatreId}
     */
    @GetMapping("/theatre/{theatreId}")
    public ResponseEntity<?> getShowsByTheatre(@PathVariable Long theatreId) {
        try {
            TheatreDetails theatre = theatreRepository.findById(theatreId)
                    .orElseThrow(() -> new RuntimeException("Theatre not found"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            // Use enum instead of string
            List<Show> shows = showRepository.findByTheatreAndStatus(theatre, ShowStatus.ACTIVE);

            List<Map<String, Object>> showsList = shows.stream()
                    .map(show -> {
                        Integer availableSeats = seatRepository.countAvailableSeats(show);
                        Map<String, Object> showMap = new HashMap<>();
                        showMap.put("id", show.getId());
                        showMap.put("movieName", show.getMovieName());
                        showMap.put("startTime", show.getStartTime().format(formatter));
                        showMap.put("endTime", show.getEndTime().format(formatter));
                        showMap.put("availableSeats", availableSeats);
                        showMap.put("pricePerSeat", show.getPricePerSeat());
                        return showMap;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "theatre", theatre.getName(),
                    "totalShows", showsList.size(),
                    "shows", showsList
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Utility endpoint to populate seats for existing shows that don't have seats
     * POST /api/shows/populate-seats/{showId}
     */
    @PostMapping("/populate-seats/{showId}")
    public ResponseEntity<?> populateSeatsForExistingShow(@PathVariable Long showId) {
        try {
            Show show = showRepository.findById(showId)
                    .orElseThrow(() -> new RuntimeException("Show not found"));

            // Check if seats already exist
            int seatCount = seatRepository.countAvailableSeats(show);
            if (seatCount > 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "Seats already exist for this show",
                                "existingSeats", seatCount
                        ));
            }

            // Create seats
            createSeats(show, show.getTotalSeats());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Seats populated successfully",
                    "showId", showId,
                    "totalSeats", show.getTotalSeats()
            ));

        } catch (Exception e) {
            log.error("❌ Error populating seats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Debug endpoint to check repository status
     * GET /api/shows/debug/{showId}
     */
    @GetMapping("/debug/{showId}")
    public ResponseEntity<?> debugShow(@PathVariable Long showId) {
        try {
            Show show = showRepository.findById(showId)
                    .orElseThrow(() -> new RuntimeException("Show not found"));

            // Check seat repository
            List<Seat> allSeats = seatRepository.findAll();
            List<Seat> showSeats = seatRepository.findByShowAndStatus(show, "AVAILABLE");
            Integer availableCount = seatRepository.countAvailableSeats(show);

            return ResponseEntity.ok(Map.of(
                    "show", Map.of(
                            "id", show.getId(),
                            "movieName", show.getMovieName(),
                            "totalSeats", show.getTotalSeats(),
                            "table", "shows_new"
                    ),
                    "repositoryStatus", Map.of(
                            "totalSeatsInDB", allSeats.size(),
                            "seatsForThisShow", showSeats.size(),
                            "availableCount", availableCount,
                            "seatRepositoryType", seatRepository.getClass().getSimpleName()
                    ),
                    "sampleSeats", allSeats.stream().limit(3).map(seat ->
                            Map.of("id", seat.getId(), "seatNumber", seat.getSeatNumber(), "status", seat.getStatus())
                    ).collect(Collectors.toList())
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", e.getMessage(),
                            "stackTrace", e.toString()
                    ));
        }
    }

    // Simple seat creation - no batch complexity
    private void createSeats(Show show, Integer totalSeats) {
        int seatsPerRow = 10;
        int seatCount = 0;

        for (int i = 0; i < (totalSeats / seatsPerRow) + 1; i++) {
            char row = (char) ('A' + i);
            int seatsInThisRow = Math.min(seatsPerRow, totalSeats - seatCount);

            for (int j = 1; j <= seatsInThisRow; j++) {
                String seatNumber = row + String.valueOf(j);

                Seat seat = new Seat();
                seat.setShow(show);
                seat.setSeatNumber(seatNumber);
                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setVersion(0L);

                seatRepository.save(seat);
                seatCount++;

                log.debug("✅ Seat created: {} for show: {}", seatNumber, show.getId());
            }
        }
        log.info("✅ Total {} seats created for show: {}", totalSeats, show.getId());
    }

    /*// Request DTO
    static class CreateShowRequest {
        public Long theatreId;
        public String movieName;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        public LocalDateTime startTime;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        public LocalDateTime endTime;
        public Integer totalSeats;
        public Double pricePerSeat;

        // Getters
        public Long getTheatreId() { return theatreId; }
        public String getMovieName() { return movieName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public Integer getTotalSeats() { return totalSeats; }
        public Double getPricePerSeat() { return pricePerSeat; }
    }*/
}




