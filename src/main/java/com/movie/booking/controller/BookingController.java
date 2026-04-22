package com.movie.booking.controller;

import com.movie.booking.service.BookingService;
import com.movie.theatrevendor.dto.BookingRequestDTO;
import com.movie.theatrevendor.dto.BookingResponseDTO;
import com.movie.theatrevendor.dto.BookingStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Create a new booking
     * POST /api/bookings/create
     *
     * Request Body:
     * {
     *   "showId": 1,
     *   "seatNumbers": ["A1", "A2", "B1"]
     * }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createBooking(
            @RequestBody BookingRequestDTO requestDTO,
            Principal principal) {

        try {
            log.info("📝 Booking request from user: {} for seats: {}",
                    principal.getName(), requestDTO.getSeatNumbers());

            BookingResponseDTO response = bookingService.createBooking(principal.getName(), requestDTO);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "success", true,
                            "message", response.getMessage(),
                            "booking", response
                    ));
        } catch (Exception e) {
            log.error("❌ Booking creation failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Get booking status
     * GET /api/bookings/{bookingId}
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBookingStatus(@PathVariable Long bookingId) {
        try {
            BookingStatusDTO status = bookingService.getBookingStatus(bookingId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "booking", status
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Get all bookings for authenticated user
     * GET /api/bookings/user/all
     */
    @GetMapping("/user/all")
    public ResponseEntity<?> getUserBookings(Principal principal) {
        try {
            List<BookingStatusDTO> bookings = bookingService.getUserBookings(principal.getName());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totalBookings", bookings.size(),
                    "bookings", bookings
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Cancel a booking
     * POST /api/bookings/{bookingId}/cancel
     */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable Long bookingId,
            @RequestParam(defaultValue = "User requested cancellation") String reason) {

        try {
            String result = bookingService.cancelBooking(bookingId, reason);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", result,
                    "bookingId", bookingId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }
}

