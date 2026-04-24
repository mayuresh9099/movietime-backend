package com.movie.booking.service;

import com.movie.theatrevendor.model.SeatStatus;
import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.BookingRequestDTO;
import com.movie.theatrevendor.dto.BookingResponseDTO;
import com.movie.theatrevendor.dto.BookingStatusDTO;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final ShowSeatRepository showSeatRepository;

    public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository, ShowRepository showRepository, UserRepository userRepository, BookingSeatRepository bookingSeatRepository, ShowSeatRepository showSeatRepository) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showSeatRepository = showSeatRepository;
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
        // ✅ ADD HERE
        if (show.getStatus() != ShowStatus.ACTIVE) {
            throw new RuntimeException("Show not active");
        }
        // 🔒 LOCK seats at DB level
        List<ShowSeat> showSeats = showSeatRepository.lockAvailableSeats(show.getId(), requestDTO.getSeatNumbers());

        if (showSeats.size() != requestDTO.getSeatNumbers().size()) {
            throw new RuntimeException("Some seats already booked");
        }

        // 🔐 Mark locked
        showSeats.forEach(ss -> {
            ss.setStatus(SeatStatus.LOCKED);
            ss.setLockedAt(LocalDateTime.now());
        });

        showSeatRepository.saveAll(showSeats);

        double totalPrice = showSeats.size() * show.getPricePerSeat();

        Booking booking = bookingRepository.save(Booking.builder().user(user).show(show).numberOfSeats(showSeats.size()).bookingStatus(BookingStatus.PENDING).totalPrice(totalPrice).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).paymentTransactionId(UUID.randomUUID().toString()).build());

        // ✅ Mapping
        List<BookingSeat> mappings = showSeats.stream().map(ss -> {
            BookingSeat bs = new BookingSeat();
            bs.setBooking(booking);
            bs.setShowSeat(ss);
            return bs;
        }).toList();

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

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // ✅ Already confirmed
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            return buildBookingResponse(booking, "Already confirmed");
        }

        // 🔥 Fetch booking-seat mappings
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

        // ✅ Extract ShowSeats
        List<ShowSeat> showSeats = bookingSeats.stream()
                .map(BookingSeat::getShowSeat)
                .toList();

        // 🔒 Validate + update seats
        for (ShowSeat ss : showSeats) {
            if (ss.getStatus() != SeatStatus.LOCKED) {
                throw new RuntimeException("Seat " + ss.getSeat().getSeatNumber() + " is not LOCKED");
            }

            ss.setStatus(SeatStatus.BOOKED);
            ss.setLockedAt(null);
        }

        // ✅ Save updated seats
        showSeatRepository.saveAll(showSeats);

        // ✅ Update booking
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());

        // 🔥 ADD THIS BLOCK (critical)
        Show show = booking.getShow();
        int bookedSeats = showSeats.size();
        if (show.getAvailableSeats() < bookedSeats) {
            throw new RuntimeException("Inconsistent seat count detected");
        }
        show.setAvailableSeats(show.getAvailableSeats() - bookedSeats);
        showRepository.save(show);

        // ✅ Save booking
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
        List<String> seatNumbers = bookingSeatRepository.findSeatNumbersByBookingId(booking.getId());

// 4️⃣ Build response
        return BookingStatusDTO.builder().bookingId(booking.getId()).status(booking.getBookingStatus().toString()).movieName(booking.getShow().getMovieName()).showTime(booking.getShow().getStartTime()).theatre(booking.getShow().getTheatre().getName()).bookedSeats(seatNumbers).totalPrice(booking.getTotalPrice()).confirmedAt(booking.getConfirmedAt()).cancelledAt(booking.getCancelledAt()).build();
    }

    /**
     * Get all bookings for a user
     */
    public List<BookingStatusDTO> getUserBookings(String userEmail) {

        User user = userRepository.findByEmail(userEmail).orElseThrow(() -> new RuntimeException("User not found"));

        // 1️⃣ Fetch all bookings ONCE
        List<Booking> bookings = bookingRepository.findByUser(user);

        // 2️⃣ Fetch all seat mappings ONCE
        Map<Long, List<String>> bookingSeatMap = bookingSeatRepository.findSeatNumbersByUser(user).stream().collect(Collectors.groupingBy(row -> (Long) row[0], Collectors.mapping(row -> (String) row[1], Collectors.toList())));

        // 3️⃣ Build response
        return bookings.stream().map(booking -> BookingStatusDTO.builder().bookingId(booking.getId()).status(booking.getBookingStatus().name()).bookedSeats(bookingSeatMap.getOrDefault(booking.getId(), List.of())).totalPrice(booking.getTotalPrice()).confirmedAt(booking.getConfirmedAt()).cancelledAt(booking.getCancelledAt()).build()).toList();
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelBookingInternal(Long bookingId, String reason) {

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found"));

        // 🔓 Fetch mapping
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);

        // ✅ WORK WITH ShowSeat (NOT Seat)
        List<ShowSeat> showSeats = bookingSeats.stream().map(BookingSeat::getShowSeat).toList();

        // 🔄 Release seats
        showSeats.forEach(ss -> {
            ss.setStatus(SeatStatus.AVAILABLE);
            ss.setLockedAt(null);
        });

        showSeatRepository.saveAll(showSeats);

        // ❗ Remove mapping
        bookingSeatRepository.deleteByBookingId(bookingId);

        Show show = booking.getShow();
        int seatsToRestore = showSeats.size();
        show.setAvailableSeats(show.getAvailableSeats() + seatsToRestore);

        showRepository.save(show);
        // 🔄 Update booking
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        bookingRepository.save(booking);

        log.warn("⚠️ Booking {} cancelled: {}", bookingId, reason);
    }

    /**
     * Build response DTO
     */

    @Transactional
    public BookingStatusDTO cancelBooking(Long bookingId, String reason, String userEmail) {

        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found"));

        // 🔐 Authorization
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new RuntimeException("Unauthorized cancellation");
        }

        // ❌ Already cancelled
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            return buildBookingStatusDTO(booking, "Already cancelled");
        }

        // ❌ Cannot cancel after show started
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED && booking.getShow().getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot cancel after show has started");
        }

        // 🔓 Fetch mapping
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());

        // ✅ USE ShowSeat
        List<ShowSeat> showSeats = bookingSeats.stream().map(BookingSeat::getShowSeat).toList();

        // 🔄 Release seats
        for (ShowSeat ss : showSeats) {
            ss.setStatus(SeatStatus.AVAILABLE);
            ss.setLockedAt(null);
        }

        showSeatRepository.saveAll(showSeats);

        // ❗ Remove mapping
        bookingSeatRepository.deleteByBookingId(booking.getId());

        // 🔄 Update booking
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());

        bookingRepository.save(booking);

        return buildBookingStatusDTO(booking, "Booking cancelled successfully");
    }

    private BookingStatusDTO buildBookingStatusDTO(Booking booking, String message) {

        List<String> seats = bookingSeatRepository.findByBookingId(booking.getId()).stream().map(bs -> bs.getShowSeat().getSeat().getSeatNumber()).toList();

        return BookingStatusDTO.builder().bookingId(booking.getId()).status(booking.getBookingStatus().name()).movieName(booking.getShow().getMovieName()).showTime(booking.getShow().getStartTime()).theatre(booking.getShow().getTheatre().getName()).bookedSeats(seats).totalPrice(booking.getTotalPrice()).confirmedAt(booking.getConfirmedAt()).cancelledAt(booking.getCancelledAt()).build();
    }

    private BookingResponseDTO buildBookingResponse(Booking booking, String message) {

        List<String> seats = bookingSeatRepository.findByBookingId(booking.getId()).stream().map(bs -> bs.getShowSeat().getSeat().getSeatNumber()).toList();

        return BookingResponseDTO.builder().bookingId(booking.getId()).movieName(booking.getShow().getMovieName()).bookedSeats(seats).numberOfSeats(seats.size()).totalPrice(booking.getTotalPrice()).bookingStatus(booking.getBookingStatus().name()).bookingTime(booking.getCreatedAt()).message(message).build();
    }
}

