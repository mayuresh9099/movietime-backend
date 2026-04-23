package com.movie.booking.service;

import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.booking.event.NotificationEvent;
import com.movie.booking.kafka.BookingEventProducer;
import com.movie.theatrevendor.model.SeatStatus;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.BookingRequestDTO;
import com.movie.theatrevendor.dto.BookingResponseDTO;
import com.movie.theatrevendor.dto.BookingStatusDTO;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.BookingSeatRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.ShowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final BookingSeatRepository bookingSeatRepository;

    public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository, ShowRepository showRepository, UserRepository userRepository, BookingSeatRepository bookingSeatRepository) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.bookingSeatRepository = bookingSeatRepository;
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

        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

        Show show = showRepository.findById(requestDTO.getShowId()).orElseThrow(() -> new RuntimeException("Show not found"));

        if (show.getStatus() != ShowStatus.ACTIVE) {
            throw new RuntimeException("Show not active");
        }

        // 🔒 Lock seats
        List<Seat> seats = seatRepository.findAvailableSeatsForBooking(show, requestDTO.getSeatNumbers());

        if (seats.size() != requestDTO.getSeatNumbers().size()) {
            throw new RuntimeException("Some seats not available");
        }

        seats.forEach(seat -> {
            seat.setStatus(SeatStatus.LOCKED);
            seat.setLockedAt(LocalDateTime.now());
        });

        seatRepository.saveAll(seats);

        double totalPrice = seats.size() * show.getPricePerSeat();

        Booking booking = bookingRepository.save(Booking.builder().user(user).show(show).numberOfSeats(seats.size()).totalPrice(totalPrice).bookingStatus(BookingStatus.PENDING).paymentTransactionId(UUID.randomUUID().toString()).createdAt(LocalDateTime.now()).build());

        // ✅ CREATE MAPPING
        List<BookingSeat> mappings = seats.stream().map(seat -> BookingSeat.builder().id(new BookingSeat.BookingSeatId(booking.getId(), seat.getId())).booking(booking).seat(seat).build()).toList();

        bookingSeatRepository.saveAll(mappings);

        // simulate payment
        if (!simulatePaymentProcessing(booking)) {
            cancelBookingInternal(booking.getId(), "Payment failed");
            throw new RuntimeException("Payment failed");
        }

        return confirmBooking(booking.getId());
    }

    @Transactional
    public BookingResponseDTO confirmBooking(Long bookingId) {

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found"));

        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            return buildBookingResponse(booking, "Already confirmed");
        }

            List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

        List<Seat> seats = bookingSeats.stream().map(BookingSeat::getSeat).toList();

        for (Seat seat : seats) {
            if (seat.getStatus() != SeatStatus.LOCKED) {
                throw new RuntimeException("Seat not in LOCKED state");
            }
            seat.setStatus(SeatStatus.BOOKED);
            seat.setLockedAt(null);
        }

        seatRepository.saveAll(seats);

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        bookingRepository.save(booking);

        return buildBookingResponse(booking, "Booking confirmed");
    }

    /**
     * Get booking details
     */
    public BookingStatusDTO getBookingStatus(Long bookingId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = auth.getName();
        // 1️⃣ Fetch booking
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found"));

        // 2️⃣ 🔐 Authorization check
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized access to booking");
        }

        // 3️⃣ Extract seat numbers safely
        List<String> seatNumbers = bookingSeatRepository
                .findSeatNumbersByBookingId(booking.getId());

// 4️⃣ Build response
        return BookingStatusDTO.builder()
                .bookingId(booking.getId())
                .status(booking.getBookingStatus().toString())
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

        // 1️⃣ Fetch all bookings ONCE
        List<Booking> bookings = bookingRepository.findByUser(user);

        // 2️⃣ Fetch all seat mappings ONCE
        Map<Long, List<String>> bookingSeatMap = bookingSeatRepository
                .findSeatNumbersByUser(user)
                .stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        // 3️⃣ Build response
        return bookings.stream()
                .map(booking -> BookingStatusDTO.builder()
                        .bookingId(booking.getId())
                        .status(booking.getBookingStatus().name())
                        .bookedSeats(bookingSeatMap.getOrDefault(booking.getId(), List.of()))
                        .totalPrice(booking.getTotalPrice())
                        .confirmedAt(booking.getConfirmedAt())
                        .cancelledAt(booking.getCancelledAt())
                        .build())
                .toList();
    }

    /**
     * Simulate payment processing
     * In production: integrate with Stripe, PayPal, RazorPay, etc.
     */
    private boolean simulatePaymentProcessing(Booking booking) {
        log.info("💰 Processing payment for booking: {} | Amount: ₹{}", booking.getId(), booking.getTotalPrice());

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
    public void cancelBookingInternal(Long bookingId, String reason) {

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found"));

        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

        List<Seat> seats = bookingSeats.stream().map(BookingSeat::getSeat).toList();

        seats.forEach(seat -> {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setLockedAt(null);
        });

        seatRepository.saveAll(seats);

        bookingSeatRepository.deleteByBookingId(bookingId);

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());

        bookingRepository.save(booking);

        log.warn("Booking {} cancelled: {}", bookingId, reason);
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

        // 🔓 Fetch seats via mapping (CORRECT)
        List<BookingSeat> bookingSeats = bookingSeatRepository
                .findByBookingId(booking.getId());

        List<Seat> seats = bookingSeats.stream()
                .map(BookingSeat::getSeat)
                .toList();

        // 🔄 Release seats
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setLockedAt(null);
        }

        seatRepository.saveAll(seats);

        // ❗ Remove mapping
        bookingSeatRepository.deleteByBookingId(booking.getId());

        // 🔄 Update booking (ONLY ONCE)
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        bookingRepository.save(booking);

        return buildBookingStatusDTO(booking, "Booking cancelled successfully");
    }

    private BookingStatusDTO buildBookingStatusDTO(Booking booking, String message) {

        List<String> seats = bookingSeatRepository
                .findByBookingId(booking.getId())
                .stream()
                .map(bs -> bs.getSeat().getSeatNumber())
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
        List<String> seats = bookingSeatRepository
                .findByBookingId(booking.getId())
                .stream()
                .map(bs -> bs.getSeat().getSeatNumber())
                .toList();
        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .movieName(booking.getShow().getMovieName())
                .bookedSeats(seats)
                .numberOfSeats(seats.size())
                .totalPrice(booking.getTotalPrice())
                .bookingStatus(booking.getBookingStatus().name())
                .bookingTime(booking.getCreatedAt())
                .message(message)
                .build();
    }
}

