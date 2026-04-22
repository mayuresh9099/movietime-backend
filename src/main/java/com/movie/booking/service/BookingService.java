package com.movie.booking.service;

import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.booking.event.NotificationEvent;
import com.movie.booking.kafka.BookingEventProducer;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.BookingRequestDTO;
import com.movie.theatrevendor.dto.BookingResponseDTO;
import com.movie.theatrevendor.dto.BookingStatusDTO;
import com.movie.theatrevendor.model.Booking;
import com.movie.theatrevendor.model.BookingStatus;
import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.model.Show;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.ShowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponseDTO createBooking(String userEmail, BookingRequestDTO requestDTO) {
        log.info("🎬 Creating booking for user: {} with seats: {}", userEmail, requestDTO.getSeatNumbers());

        try {
            // 1️⃣ Fetch current user
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));

            // 2️⃣ Fetch show
            Show show = showRepository.findById(requestDTO.getShowId())
                    .orElseThrow(() -> new RuntimeException("Show not found: " + requestDTO.getShowId()));

            // 3️⃣ Validate show status
            if (!"ACTIVE".equals(show.getStatus())) {
                throw new RuntimeException("Show is not available for booking");
            }

            // 4️⃣ PESSIMISTIC LOCK: Lock selected seats (SELECT FOR UPDATE)
            // This prevents concurrent bookings of the same seats
            List<Seat> requestedSeats = seatRepository.findAvailableSeatsForBooking(
                    show,
                    requestDTO.getSeatNumbers()
            );

            // 5️⃣ Validate all requested seats are available
            if (requestedSeats.size() != requestDTO.getSeatNumbers().size()) {
                log.warn("Some seats are not available. Requested: {}, Found: {}",
                        requestDTO.getSeatNumbers().size(), requestedSeats.size());
                throw new RuntimeException("Some seats are not available. Please select different seats.");
            }

            // 6️⃣ Create booking entity with PENDING status
            Double totalPrice = requestDTO.getSeatNumbers().size() * show.getPricePerSeat();

            Booking booking = Booking.builder()
                    .user(user)
                    .show(show)
                    .seats(requestedSeats)
                    .numberOfSeats(requestDTO.getSeatNumbers().size())
                    .totalPrice(totalPrice)
                    .bookingStatus(BookingStatus.PENDING)
                    .paymentTransactionId(UUID.randomUUID().toString())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            // 7️⃣ Lock seats (mark as LOCKED for this transaction)
            for (Seat seat : requestedSeats) {
                seat.setStatus("LOCKED");
                seatRepository.save(seat);
            }

            // 8️⃣ Save booking to database
            Booking savedBooking = bookingRepository.save(booking);
            log.info("✅ Booking created with ID: {} | Status: PENDING | Seats: {}",
                    savedBooking.getId(), requestDTO.getSeatNumbers());

            // 9️⃣ STEP 2: Simulate payment processing
            boolean paymentSuccess = simulatePaymentProcessing(savedBooking);

            if (paymentSuccess) {
                // 🔟 STEP 3: Payment successful - update booking status to CONFIRMED
                return confirmBooking(savedBooking.getId());
            } else {
                // Payment failed - rollback (automatic via @Transactional or manual cancel)
                cancelBooking(savedBooking.getId(), "Payment Failed");
                throw new RuntimeException("Payment processing failed. Booking cancelled.");
            }

        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("❌ Optimistic locking failed - concurrent modification detected", e);
            throw new RuntimeException("Booking failed: Another user booked these seats. Please try again.");
        } catch (Exception e) {
            log.error("❌ Booking creation failed", e);
            throw e;
        }
    }

    /**
     * STEP 3: Confirm booking after successful payment
     * Publishes BookingConfirmedEvent to Kafka
     * Kafka consumer updates seat availability
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponseDTO confirmBooking(Long bookingId) {
        log.info("💳 Confirming booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Update booking status to CONFIRMED
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        Booking confirmedBooking = bookingRepository.save(booking);

        // Publish event for Kafka consumer to update seats
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(confirmedBooking.getId())
                .userId(confirmedBooking.getUser().getId())
                .userEmail(confirmedBooking.getUser().getEmail())
                .movieName(confirmedBooking.getShow().getMovieName())
                .bookedSeats(confirmedBooking.getSeats().stream()
                        .map(Seat::getSeatNumber)
                        .collect(Collectors.toList()))
                .totalPrice(confirmedBooking.getTotalPrice())
                .eventTime(LocalDateTime.now())
                .build();

        eventProducer.publishBookingConfirmedEvent(event);

        // Send notification email
        NotificationEvent notification = NotificationEvent.builder()
                .userId(confirmedBooking.getUser().getId())
                .userEmail(confirmedBooking.getUser().getEmail())
                .messageType("BOOKING_CONFIRMED")
                .message(String.format("Your booking for %s is confirmed. Booking ID: %d",
                        confirmedBooking.getShow().getMovieName(), bookingId))
                .eventTime(LocalDateTime.now())
                .build();

        eventProducer.publishNotificationEvent(notification);

        log.info("✅ Booking confirmed: {} | Seats: {} | Price: ₹{}",
                bookingId,
                confirmedBooking.getSeats().stream()
                        .map(Seat::getSeatNumber)
                        .collect(Collectors.joining(", ")),
                confirmedBooking.getTotalPrice());

        return buildBookingResponse(confirmedBooking, "Booking confirmed successfully!");
    }

    /**
     * STEP 4: Cancel booking and release seats
     * Publishes BookingCancelledEvent for Kafka to release seats
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String cancelBooking(Long bookingId, String reason) {
        log.info("❌ Cancelling booking: {} | Reason: {}", bookingId, reason);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if ("CANCELLED".equals(booking.getBookingStatus())) {
            throw new RuntimeException("Booking already cancelled");
        }

        // Update booking status to CANCELLED
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        Booking cancelledBooking = bookingRepository.save(booking);

        // Release locks on seats
        for (Seat seat : booking.getSeats()) {
            seat.setStatus("AVAILABLE");
            seat.setBookingId(null);
            seatRepository.save(seat);
        }

        // Publish cancellation event
        BookingCancelledEvent event = BookingCancelledEvent.builder()
                .bookingId(cancelledBooking.getId())
                .userId(cancelledBooking.getUser().getId())
                .userEmail(cancelledBooking.getUser().getEmail())
                .cancelledSeats(cancelledBooking.getSeats().stream()
                        .map(Seat::getSeatNumber)
                        .collect(Collectors.toList()))
                .refundAmount(cancelledBooking.getTotalPrice())
                .eventTime(LocalDateTime.now())
                .cancellationReason(reason)
                .build();

        eventProducer.publishBookingCancelledEvent(event);

        // Send cancellation notification
        NotificationEvent notification = NotificationEvent.builder()
                .userId(cancelledBooking.getUser().getId())
                .userEmail(cancelledBooking.getUser().getEmail())
                .messageType("BOOKING_CANCELLED")
                .message(String.format("Your booking #%d has been cancelled. Refund: ₹%.2f",
                        bookingId, cancelledBooking.getTotalPrice()))
                .eventTime(LocalDateTime.now())
                .build();

        eventProducer.publishNotificationEvent(notification);

        log.info("✅ Booking cancelled: {} | Refund: ₹{}", bookingId, cancelledBooking.getTotalPrice());
        return String.format("Booking cancelled. Refund of ₹%.2f will be processed",
                cancelledBooking.getTotalPrice());
    }

    /**
     * Get booking details
     */
    public BookingStatusDTO getBookingStatus(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        return BookingStatusDTO.builder()
                .bookingId(booking.getId())
                .bookingStatus(booking.getBookingStatus().name())
                .bookedSeats(booking.getSeats().stream()
                        .map(Seat::getSeatNumber)
                        .collect(Collectors.toList()))
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
                        .bookingStatus(booking.getBookingStatus().name())
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

    /**
     * Build response DTO
     */
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

