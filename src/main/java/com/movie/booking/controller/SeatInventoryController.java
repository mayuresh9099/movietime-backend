package com.movie.booking.controller;

import com.movie.booking.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatInventoryController {

    private final SeatInventoryService seatInventoryService;


        @PostMapping("/allocate/{showId}")
        public ResponseEntity<?> allocateSeats(@PathVariable Long showId) {
            try {
                seatInventoryService.allocateSeatsForShow(showId);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Seats allocated successfully",
                        "showId", showId
                ));

            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", e.getMessage()));
            }
        }

        @GetMapping("/inventory/{showId}")
        public ResponseEntity<?> getSeatInventory(@PathVariable Long showId) {
            try {
                SeatInventoryService.SeatInventoryStatus status =
                        seatInventoryService.getSeatInventoryStatus(showId);

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "showId", showId,
                        "totalSeats", status.getTotalSeats(),
                        "availableSeats", status.getAvailableSeats(),
                        "bookedSeats", status.getBookedSeats(),
                        "lockedSeats", status.getLockedSeats(),
                        "availableSeatNumbers", status.getAvailableSeatNumbers()
                ));

            } catch (Exception e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", e.getMessage()));
            }
        }

    /**
     * Block seats for maintenance
     * POST /api/seats/block/{showId}
     *
     * Request Body:
     * {
     *   "seatNumbers": ["A1", "A2", "B1"]
     * }
     */
    @PostMapping("/block/{showId}")
    public ResponseEntity<?> blockSeats(@PathVariable Long showId, @RequestBody SeatOperationRequest request) {
        try {
            seatInventoryService.blockSeatsForMaintenance(showId, request.getSeatNumbers());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Seats blocked for maintenance",
                    "showId", showId,
                    "blockedSeats", request.getSeatNumbers()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Unblock seats from maintenance
     * POST /api/seats/unblock/{showId}
     *
     * Request Body:
     * {
     *   "seatNumbers": ["A1", "A2", "B1"]
     * }
     */
    @PostMapping("/unblock/{showId}")
    public ResponseEntity<?> unblockSeats(@PathVariable Long showId, @RequestBody SeatOperationRequest request) {
        try {
            seatInventoryService.unblockSeatsFromMaintenance(showId, request.getSeatNumbers());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Seats unblocked from maintenance",
                    "showId", showId,
                    "unblockedSeats", request.getSeatNumbers()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get seat map for a show
     * GET /api/seats/{showId}/status
     */
    @GetMapping("/{showId}/status")
    public ResponseEntity<?> getSeatMap(@PathVariable Long showId) {
        try {
            List<Map<String, Object>> seatMap = seatInventoryService.getSeatMap(showId);

            return ResponseEntity.ok(Map.of(
                    "seatMap", seatMap
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // Request DTO
    public static class SeatOperationRequest {
        private List<String> seatNumbers;

        public List<String> getSeatNumbers() { return seatNumbers; }
        public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
    }
}
