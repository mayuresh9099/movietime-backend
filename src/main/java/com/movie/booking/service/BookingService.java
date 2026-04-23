package com.movie.booking.service;

import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.booking.event.NotificationEvent;
import com.movie.booking.kafka.BookingEventProducer;
import com.movie.common.util.model.SeatStatus;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.BookingRequestDTO;
import com.movie.theatrevendor.dto.BookingResponseDTO;
import com.movie.theatrevendor.dto.BookingStatusDTO;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.ShowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.movie.common.util.model.SeatStatus.AVAILABLE;
import static com.movie.common.util.model.SeatStatus.BOOKED;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final BookingEventProducer eventProducer;

    public BookingService(BookingRepository bookingRepository,
                         SeatRepository seatRepository,
                         ShowRepository showRepository,
                         UserRepository userRepository,
                         BookingEventProducer eventProducer) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.eventProducer = eventProducer;
    }

    /**
     * Step 1: CREATE BOOKING with PENDING status
     * Uses pessimistic locking (SELECT FOR UPDATE) to prevent double booking
     * ACID Properties:
     * - Atomicity: All seats locked together or none
     * - Consistency: Booking status always PENDING initially
     * - Isolation: SERIALIZABLE prevents concurrent booking of same seats
     * - Durability: Committed to DB
     */
    @Transactional
    public BookingResponseDTO createBooking(String userEmail, BookingRequestDTO requestDTO) {

        log.info("🎬 Creating booking for user: {} with seats: {}", userEmail, requestDTO.getSeatNumbers());

        // 1️⃣ Fetch user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

        // 2️⃣ Fetch show
        Show show = showRepository.findById(requestDTO.getShowId())
                .orElseThrow(() -> new RuntimeException("Show not found: " + requestDTO.getShowId()));

        // 3️⃣ Validate show status (ENUM FIX)
        if (show.getStatus() != ShowStatus.ACTIVE) {
            throw new RuntimeException("Show is not available for booking");
        }

        // 4️⃣ Lock & fetch available seats (PESSIMISTIC LOCK)
        List<Seat> requestedSeats = seatRepository.findAvailableSeatsForBooking(
                show,
                requestDTO.getSeatNumbers()
        );

        // 5️⃣ Validate seat availability
        if (requestedSeats.size() != requestDTO.getSeatNumbers().size()) {
            throw new RuntimeException("Some seats are not available. Please select different seats.");
        }

        // 6️⃣ Calculate total price
        double totalPrice = requestedSeats.size() * show.getPricePerSeat();

        // 7️⃣ Lock seats
        for (Seat seat : requestedSeats) {
            seat.setStatus(SeatStatus.LOCKED); // ✅ ENUM
        }
        seatRepository.saveAll(requestedSeats);

        // 8️⃣ Create booking (PENDING)
        Booking booking = Booking.builder()
                .user(user)
                .show(show)
                .seats(requestedSeats)
                .numberOfSeats(requestedSeats.size())
                .totalPrice(totalPrice)
                .bookingStatus(BookingStatus.PENDING)
                .paymentTransactionId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        log.info("✅ Booking created with ID: {} | Seats: {}", savedBooking.getId(), requestDTO.getSeatNumbers());

        // 9️⃣ Simulate payment
        boolean paymentSuccess = simulatePaymentProcessing(savedBooking);

        if (!paymentSuccess) {
            cancelBooking(savedBooking.getId(), "Payment Failed");
            throw new RuntimeException("Payment failed. Booking cancelled.");
        }

        // 🔟 Confirm booking
        return confirmBooking(savedBooking.getId());
    }
    /**
     * STEP 3: Confirm booking after successful payment
     * Publishes BookingConfirmedEvent to Kafka
     * Kafka consumer updates seat availability
     */


    @Transactional
    public BookingResponseDTO confirmBooking(Long bookingId) {

        log.info("💳 Confirming booking: {}", bookingId);

        // 1️⃣ Fetch booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // 2️⃣ ✅ Idempotency (VERY IMPORTANT)
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            log.warn("⚠️ Booking already confirmed: {}", bookingId);
            return buildBookingResponse(booking, "Booking already confirmed");
        }

        // 3️⃣ Prevent invalid transitions
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new RuntimeException("Cannot confirm a cancelled booking");
        }

        // 4️⃣ Validate show timing
        if (booking.getShow().getEndTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot confirm booking for completed show");
        }

        // 5️⃣ Validate and update seat status
        // 1️⃣ Validate all seats first
        for (Seat seat : booking.getSeats()) {

            if (seat.getStatus() == null) {
                throw new RuntimeException("Seat " + seat.getSeatNumber() + " has invalid status");
            }

            if (seat.getStatus() != SeatStatus.LOCKED) {

                String reason = switch (seat.getStatus()) {
                    case AVAILABLE -> "not reserved";
                    case BOOKED -> "already booked";
                    default -> "in invalid state";
                };

                throw new RuntimeException(
                        "Seat " + seat.getSeatNumber() + " is " + reason
                );
            }
        }

        seatRepository.saveAll(booking.getSeats());

        // 6️⃣ Update booking
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        Booking confirmedBooking = bookingRepository.save(booking);

        // 7️⃣ Extract seat numbers (single source of truth)
        List<String> seatNumbers = confirmedBooking.getSeats()
                .stream()
                .map(Seat::getSeatNumber)
                .toList();

        // 8️⃣ Prepare events (but DO NOT publish yet)
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(confirmedBooking.getId())
                .userId(confirmedBooking.getUser().getId())
                .userEmail(confirmedBooking.getUser().getEmail())
                .movieName(confirmedBooking.getShow().getMovieName())
                .bookedSeats(seatNumbers)
                .totalPrice(confirmedBooking.getTotalPrice())
                .eventTime(LocalDateTime.now())
                .build();

        NotificationEvent notification = NotificationEvent.builder()
                .userId(confirmedBooking.getUser().getId())
                .userEmail(confirmedBooking.getUser().getEmail())
                .messageType("BOOKING_CONFIRMED")
                .message(String.format(
                        "Your booking for %s is confirmed. Booking ID: %d | Seats: %s",
                        confirmedBooking.getShow().getMovieName(),
                        bookingId,
                        String.join(", ", seatNumbers)
                ))
                .eventTime(LocalDateTime.now())
                .build();

        // 9️⃣ ✅ Publish AFTER transaction commit (CRITICAL)
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventProducer.publishBookingConfirmedEvent(event);
                        eventProducer.publishNotificationEvent(notification);
                    }
                }
        );

        log.info("✅ Booking confirmed: {} | Seats: {} | Price: ₹{}",
                bookingId,
                String.join(", ", seatNumbers),
                confirmedBooking.getTotalPrice());

        // 🔟 Return response
        return buildBookingResponse(confirmedBooking, "Booking confirmed successfully!");
    }
    /**
     * Get booking details
     */
    public BookingStatusDTO getBookingStatus(Long bookingId) {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();
        String userEmail = auth.getName();
        // 1️⃣ Fetch booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // 2️⃣ 🔐 Authorization check
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized access to booking");
        }

        // 3️⃣ Extract seat numbers safely
        List<String> seatNumbers = booking.getSeats() == null
                ? Collections.emptyList()
                : booking.getSeats().stream()
                  .map(Seat::getSeatNumber)
                  .toList();

        // 4️⃣ Build response
        return BookingStatusDTO.builder()
                .bookingId(booking.getId())
                .status(booking.getBookingStatus().toString()) // cleaner than name()
                .movieName(booking.getShow().getMovieName())
                .showTime(booking.getShow().getStartTime())
                .theatre(booking.getShow().getTheatre().getName())
                .bookedSeats(seatNumbers)
                .totalPrice(booking.getTotalPrice())
                .confirmedAt(booking.getConfirmedAt())
                .cancelledAt(booking.getCancelledAt())
                .build();
    }

    /**
     * Get all bookings for a user
     */
    public List<BookingStatusDTO> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return bookingRepository.findByUser(user).stream()
                .map(booking -> BookingStatusDTO.builder()
                        .bookingId(booking.getId())
                        .status(booking.getBookingStatus().name())
                        .bookedSeats(booking.getSeats().stream()
                                .map(Seat::getSeatNumber)
                                .collect(Collectors.toList()))
                        .totalPrice(booking.getTotalPrice())
                        .confirmedAt(booking.getConfirmedAt())
                        .cancelledAt(booking.getCancelledAt())
                        .build())
                .collect(Collectors.toList());
    }



    /**
     * Simulate payment processing
     * In production: integrate with Stripe, PayPal, RazorPay, etc.
     */
    private boolean simulatePaymentProcessing(Booking booking) {
        log.info("💰 Processing payment for booking: {} | Amount: ₹{}",
                booking.getId(), booking.getTotalPrice());

        try {
            // Simulate payment API call (90% success rate for demo)
            boolean paymentSuccess = Math.random() < 0.9;

            if (paymentSuccess) {
                log.info("✅ Payment successful for booking: {}", booking.getId());
                return true;
            } else {
                log.warn("❌ Payment failed for booking: {}", booking.getId());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Payment processing error", e);
            return false;
        }
    }

    @Transactional
    public void cancelBooking(Long bookingId, String reason) {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now());

        // Release seats
        for (Seat seat : booking.getSeats()) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }
        seatRepository.saveAll(booking.getSeats());

        log.warn("⚠️ Booking {} cancelled due to: {}", bookingId, reason);
    }
    /**
     * Build response DTO
     */

    @Transactional
    public BookingStatusDTO cancelBooking(Long bookingId, String reason, String userEmail) {

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // 🔐 Authorization check
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized cancellation");
        }

        // ❌ Prevent invalid states
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            return buildBookingStatusDTO(booking, "Already cancelled");
        }

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED &&
                booking.getShow().getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot cancel after show has started");
        }

        // 🔄 Update booking
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());

        // 🔓 Release seats
        for (Seat seat : booking.getSeats()) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }

        seatRepository.saveAll(booking.getSeats());
        bookingRepository.save(booking);

        return buildBookingStatusDTO(booking, "Booking cancelled successfully");
    }

    private BookingStatusDTO buildBookingStatusDTO(Booking booking, String message) {

        List<String> seats = booking.getSeats().stream()
                .map(Seat::getSeatNumber)
                .toList();

        return BookingStatusDTO.builder()
                .bookingId(booking.getId())
                .status(booking.getBookingStatus().name())
                .movieName(booking.getShow().getMovieName())
                .showTime(booking.getShow().getStartTime())
                .theatre(booking.getShow().getTheatre().getName())
                .bookedSeats(seats)
                .totalPrice(booking.getTotalPrice())
                .confirmedAt(booking.getConfirmedAt())
                .cancelledAt(booking.getCancelledAt())
                .build();
    }

    private BookingResponseDTO buildBookingResponse(Booking booking, String message) {
        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .movieName(booking.getShow().getMovieName())
                .bookedSeats(booking.getSeats().stream()
                        .map(Seat::getSeatNumber)
                        .collect(Collectors.toList()))
                .numberOfSeats(booking.getNumberOfSeats())
                .totalPrice(booking.getTotalPrice())
                .bookingStatus(booking.getBookingStatus().name())
                .bookingTime(booking.getCreatedAt())
                .message(message)
                .build();
    }
}

