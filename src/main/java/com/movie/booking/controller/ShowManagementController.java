package com.movie.booking.controller;

import com.movie.booking.service.ShowManagementService;
import com.movie.theatrevendor.dto.CreateShowRequest;
import com.movie.theatrevendor.model.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/theatre/shows")
@RequiredArgsConstructor
public class ShowManagementController {

    private final ShowManagementService showManagementService;

    /**
     * Create a new show (Theatre Owner only)
     * POST /api/theatre/shows/create
     */
    @PostMapping("/create")
    public ResponseEntity<?> createShow(@RequestBody CreateShowRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerEmail = auth.getName();

            Show show = showManagementService.createShow(request, ownerEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Show created successfully",
                    "showId", show.getId(),
                    "movieName", show.getMovieName()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Update show details (Theatre Owner only)
     * PUT /api/theatre/shows/{showId}
     */
    @PutMapping("/{showId}")
    public ResponseEntity<?> updateShow(
            @PathVariable Long showId,
            @RequestBody CreateShowRequest request
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerEmail = auth.getName();

            Show updatedShow = showManagementService.updateShow(showId, request, ownerEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Show updated successfully",
                    "data", Map.of(
                            "showId", updatedShow.getId(),
                            "movieName", updatedShow.getMovieName(),
                            "startTime", updatedShow.getStartTime(),
                            "endTime", updatedShow.getEndTime(),
                            "pricePerSeat", updatedShow.getPricePerSeat()
                    )
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
    /**
     * Delete show (Theatre Owner only)
     * DELETE /api/theatre/shows/{showId}
     */
    @DeleteMapping("/{showId}")
    public ResponseEntity<?> deleteShow(@PathVariable Long showId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerEmail = auth.getName();

            showManagementService.deleteShow(showId, ownerEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Show deleted successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get all shows for theatre owner
     * GET /api/theatre/shows/my-shows
     */
    @GetMapping("/my-shows")
    public ResponseEntity<?> getMyShows() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerEmail = auth.getName();

            List<Show> shows = showManagementService.getTheatreShows(ownerEmail);

            List<Map<String, Object>> showList = shows.stream()
                    .map(show -> Map.<String, Object>of(
                            "showId", show.getId(),
                            "movieName", show.getMovieName(),
                            "theatreName", show.getTheatre().getName(),
                            "startTime", show.getStartTime(),
                            "endTime", show.getEndTime(),
                            "totalSeats", show.getTotalSeats(),
                            "availableSeats", show.getAvailableSeats(),
                            "pricePerSeat", show.getPricePerSeat(),
                            "status", show.getStatus()
                    ))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totalShows", shows.size(),
                    "shows", showList
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Create seats for an existing show (Theatre Owner only)
     * POST /api/theatre/shows/{showId}/create-seats
     */
    @PostMapping("/{showId}/create-seats")
    public ResponseEntity<?> createSeatsForShow(@PathVariable Long showId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String ownerEmail = auth.getName();

            showManagementService.createSeatsForExistingShow(showId, ownerEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Seats created successfully for show"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
