package com.movie.booking.controller;

import com.movie.booking.service.BulkOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bulk")
@RequiredArgsConstructor
public class BulkOperationsController {

    private final BulkOperationsService bulkOperationsService;

    /**
     * Bulk booking for multiple seats
     * POST /api/bulk/book
     *
     * Request Body:
     * {
     *   "showId": 1,
     *   "seatNumbers": ["A1", "A2", "A3", "B1", "B2"]
     * }
     */
    @PostMapping("/book")
    public ResponseEntity<?> bulkBookSeats(@RequestBody BulkBookingRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            BulkOperationsService.BulkBookingResult result =
                    bulkOperationsService.bulkBookSeats(userEmail, request.getShowId(), request.getSeatNumbers());

            if (result.isSuccess()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", result.getMessage(),
                        "bookingId", result.getBookingId(),
                        "seatsBooked", request.getSeatNumbers().size(),
                        "originalPrice", result.getDiscount().getOriginalPrice(),
                        "discountAmount", result.getDiscount().getDiscountAmount(),
                        "finalPrice", result.getDiscount().getFinalPrice(),
                        "discountDescription", result.getDiscount().getDiscountDescription()
                ));
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", result.getMessage()));
            }

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Bulk cancellation for multiple bookings
     * POST /api/bulk/cancel
     *
     * Request Body:
     * {
     *   "bookingIds": [1, 2, 3]
     * }
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> bulkCancelBookings(@RequestBody BulkCancellationRequest request) {

        try {
            // ✅ Validate request
            if (request == null || request.getBookingIds() == null || request.getBookingIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Booking IDs are required"));
            }

            // ✅ Get logged-in user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = auth.getName();

            // ✅ Call service
            BulkOperationsService.BulkCancellationResult result =
                    bulkOperationsService.bulkCancelBookings(userEmail, request.getBookingIds());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Bulk cancellation completed",
                    "successfulCancellations", result.getSuccessfulCancellations(),
                    "failedCancellations", result.getFailedCancellations(),
                    "totalBookings", request.getBookingIds().size(),
                    "errors", result.getErrors()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }
    // Request DTOs
    public static class BulkBookingRequest {
        private Long showId;
        private List<String> seatNumbers;

        public Long getShowId() { return showId; }
        public void setShowId(Long showId) { this.showId = showId; }

        public List<String> getSeatNumbers() { return seatNumbers; }
        public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
    }

    public static class BulkCancellationRequest {
        private List<Long> bookingIds;

        public List<Long> getBookingIds() { return bookingIds; }
        public void setBookingIds(List<Long> bookingIds) { this.bookingIds = bookingIds; }
    }
}
