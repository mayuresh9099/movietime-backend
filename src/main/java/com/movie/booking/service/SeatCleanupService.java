package com.movie.booking.service;

import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.BookingSeatRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to clean up orphan locks and manage seat timeouts
 *
 * RESPONSIBILITIES:
 * 1. Release seats that remain LOCKED for too long (timeout)
 * 2. Handle bookings stuck in PENDING state (payment timeout)
 * 3. Validate seat-booking associations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatCleanupService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;

    /**
     * Release seats locked for too long (no payment completed)
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    @Transactional
    public void releaseExpiredLockedSeats() {

        log.info("🧹 Checking for expired locked seats...");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        int released = showSeatRepository.releaseExpiredLocksForAllShows(threshold);

        if (released > 0) {
            log.warn("⚠️ Released {} expired locked seats", released);
        }

        log.info("✅ Locked seat cleanup completed");
    }
    /**
     * Release seats from timed-out bookings
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Transactional
    public void releasePendingBookingTimeouts() {

        log.info("🧹 Checking for pending booking timeouts...");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        List<Booking> expiredBookings =
                bookingRepository.findExpiredPendingBookings(threshold);

        for (Booking booking : expiredBookings) {

            Long bookingId = booking.getId();

            log.warn("⏱️ Expired booking: {}", bookingId);

            // Get seats via mapping
            List<BookingSeat> bookingSeats =
                    bookingSeatRepository.findByBookingId(bookingId);

            // Extract seat numbers (ShowSeat now holds the seats)
            List<String> seatNumbers = bookingSeats.stream()
                    .map(bs -> bs.getShowSeat().getSeat().getSeatNumber())
                    .toList();

            // Release seats using ShowSeatRepository
            showSeatRepository.releaseSeats(booking.getShow().getId(), seatNumbers);

            // Remove mapping
            bookingSeatRepository.deleteByBookingId(bookingId);

            // Update booking
            booking.setBookingStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());

            bookingRepository.save(booking);

            log.info("✅ Released seats for booking: {}", bookingId);
        }

        log.info("✅ Pending booking cleanup completed");
    }

    /**
     * System health check (NEW LOGIC)
     */
    @Transactional(readOnly = true)
    public SeatHealthStatus getSystemHealth() {

        // Aggregate locked seats across all shows using ShowSeatRepository
        long stuckLocks = showSeatRepository.countAllLockedSeats();

        long pendingBookings =
                bookingRepository.countByBookingStatus(BookingStatus.PENDING);

        boolean healthy = stuckLocks == 0 && pendingBookings == 0;

        return new SeatHealthStatus(
                healthy,
                (int) stuckLocks,
                pendingBookings
        );
    }

    // DTO
    public static class SeatHealthStatus {

        private final boolean healthy;
        private final int lockedSeats;
        private final long pendingBookings;

        public SeatHealthStatus(boolean healthy, int lockedSeats, long pendingBookings) {
            this.healthy = healthy;
            this.lockedSeats = lockedSeats;
            this.pendingBookings = pendingBookings;
        }

        public boolean isHealthy() { return healthy; }
        public int getLockedSeats() { return lockedSeats; }
        public long getPendingBookings() { return pendingBookings; }
    }
}


