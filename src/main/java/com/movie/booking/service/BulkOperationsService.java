package com.movie.booking.service;

import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkOperationsService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final DiscountService discountService;
    private final BookingSeatRepository bookingSeatRepository;

    @Transactional
    public BulkBookingResult bulkBookSeats(String userEmail, Long showId, List<String> seatNumbers) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        if (show.getStatus() != ShowStatus.ACTIVE) {
            throw new RuntimeException("Show not active");
        }

        // ✅ STEP 1: Fetch seats
        List<Seat> seats = seatRepository.findByShowAndSeatNumbers(showId, seatNumbers);

        if (seats.size() != seatNumbers.size()) {
            throw new RuntimeException("Some seats not found");
        }

        // ✅ STEP 2: Check already booked seats
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        int alreadyBooked = bookingSeatRepository.countBookedSeats(showId, seatIds);

        if (alreadyBooked > 0) {
            throw new RuntimeException("Some seats already booked");
        }

        // ✅ STEP 3: Calculate price
        DiscountService.DiscountCalculation discount =
                discountService.calculateDiscount(
                        seatNumbers,
                        show.getStartTime().toLocalTime(),
                        BigDecimal.valueOf(show.getPricePerSeat())
                );

        // ✅ STEP 4: Create booking
        Booking booking = Booking.builder()
                .user(user)
                .show(show)
                .numberOfSeats(seats.size())
                .totalPrice(discount.getFinalPrice().doubleValue())
                .bookingStatus(BookingStatus.PENDING)
                .paymentTransactionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .build();

        booking = bookingRepository.save(booking);

        // ✅ STEP 5: Insert BookingSeat (NO composite key manual setting)
        Booking savedBooking = bookingRepository.save(booking);

        List<ShowSeat> showSeats = showSeatRepository
                .findByShowAndSeatNumbers(show.getId(), seatNumbers);

        List<BookingSeat> mappings = showSeats.stream()
                .map(ss -> BookingSeat.builder()
                        .booking(savedBooking)
                        .showSeat(ss)   // ✅ correct field
                        .build())
                .toList();

        bookingSeatRepository.saveAll(mappings);

        // ✅ STEP 6: Simulate payment
        boolean paymentSuccess = Math.random() < 0.9;

        if (!paymentSuccess) {

            bookingSeatRepository.deleteByBookingId(booking.getId());

            booking.setBookingStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());
            bookingRepository.save(booking);

            return new BulkBookingResult(false, "Payment failed", null, null);
        }

        // ✅ STEP 7: Confirm booking
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        return new BulkBookingResult(true, "Booking confirmed", booking.getId(), discount);
    }

    /**
     * ✅ BULK CANCEL
     */
    @Transactional
    public BulkCancellationResult bulkCancelBookings(String userEmail, List<Long> bookingIds) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int success = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Long bookingId : bookingIds) {
            try {
                Booking booking = bookingRepository.findById(bookingId)
                        .orElseThrow(() -> new RuntimeException("Booking not found"));

                if (!booking.getUser().getId().equals(user.getId())) {
                    throw new RuntimeException("Unauthorized");
                }

                if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
                    throw new RuntimeException("Already cancelled");
                }

                if (booking.getShow().getStartTime().isBefore(LocalDateTime.now())) {
                    throw new RuntimeException("Show started");
                }

                // ✅ Delete mappings directly
                int deleted = bookingSeatRepository.deleteByBookingId(bookingId);

                booking.setBookingStatus(BookingStatus.CANCELLED);
                booking.setCancelledAt(LocalDateTime.now());
                bookingRepository.save(booking);

                success++;

            } catch (Exception e) {
                failed++;
                errors.add("Booking " + bookingId + ": " + e.getMessage());
            }
        }

        return new BulkCancellationResult(success, failed, errors);
    }

    // ================= DTOs =================

    public static class BulkBookingResult {
        private final boolean success;
        private final String message;
        private final Long bookingId;
        private final DiscountService.DiscountCalculation discount;

        public BulkBookingResult(boolean success, String message, Long bookingId,
                                 DiscountService.DiscountCalculation discount) {
            this.success = success;
            this.message = message;
            this.bookingId = bookingId;
            this.discount = discount;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Long getBookingId() { return bookingId; }
        public DiscountService.DiscountCalculation getDiscount() { return discount; }
    }

    public static class BulkCancellationResult {
        private final int successfulCancellations;
        private final int failedCancellations;
        private final List<String> errors;

        public BulkCancellationResult(int successfulCancellations,
                                      int failedCancellations,
                                      List<String> errors) {
            this.successfulCancellations = successfulCancellations;
            this.failedCancellations = failedCancellations;
            this.errors = errors;
        }

        public int getSuccessfulCancellations() {
            return successfulCancellations;
        }

        public int getFailedCancellations() {
            return failedCancellations;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}