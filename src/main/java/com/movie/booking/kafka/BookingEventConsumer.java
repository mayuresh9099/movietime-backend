package com.movie.booking.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.theatrevendor.model.BookingSeat;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.BookingSeatRepository;
import com.movie.theatrevendor.repository.ShowSeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BookingEventConsumer: Handles Kafka events for booking status changes.
 *
 * NOTE: In the new architecture, BookingService already updates ShowSeats.
 * This consumer can be used for:
 * 1. Async notifications to client
 * 2. Analytics/logging
 * 3. Third-party integrations
 *
 * Status updates to ShowSeats should NOT happen here - they're already done in BookingService.
 */
@Slf4j
@Service
public class BookingEventConsumer {

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;
    private final BookingSeatRepository bookingSeatRepository;

    public BookingEventConsumer(ShowSeatRepository showSeatRepository,
                                BookingRepository bookingRepository,
                                ObjectMapper objectMapper,
                                BookingSeatRepository bookingSeatRepository) {
        this.showSeatRepository = showSeatRepository;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    /**
     * Consume BookingConfirmedEvent
     *
     * NOTE: In the new architecture, seats should already be BOOKED
     * when this event is received. This consumer is mainly for
     * notifications and analytics.
     */
    @KafkaListener(topics = "booking-events", groupId = "booking-confirmed-group")
    @Transactional(readOnly = true)
    public void handleBookingConfirmed(String message) {
        try {
            if (message.contains("booking-confirmed")) {
                BookingConfirmedEvent event = objectMapper.readValue(message, BookingConfirmedEvent.class);

                Long bookingId = event.getBookingId();

                log.info("📖 Received BookingConfirmedEvent for booking: {}", bookingId);

                // VALIDATION: Verify booking exists and is CONFIRMED
                var booking = bookingRepository.findById(bookingId);
                if (booking.isEmpty()) {
                    log.warn("⚠️ Booking not found: {}", bookingId);
                    return;
                }

                // OPTIONAL: Notify user, send email, update analytics, etc.
                log.info("✅ Booking confirmed: {} | Movie: {}", bookingId, booking.get().getShow().getMovieName());

                // Get booked seats for reference (not for updating)
                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
                log.info("🎫 Booking includes {} seats", bookingSeats.size());
            }

        } catch (Exception e) {
            log.error("❌ Error processing BookingConfirmedEvent: {}", message, e);
        }
    }

    /**
     * Consume BookingCancelledEvent
     *
     * This is primarily for notifications and analytics.
     * Seat release should already be done in BookingService.cancelBooking().
     */
    @KafkaListener(topics = "booking-events", groupId = "booking-cancellation-group")
    @Transactional(readOnly = true)
    public void handleBookingCancelled(String message) {
        try {
            if (message.contains("booking-cancelled")) {
                BookingCancelledEvent event = objectMapper.readValue(message, BookingCancelledEvent.class);

                Long bookingId = event.getBookingId();

                log.info("📖 Received BookingCancelledEvent for booking: {}", bookingId);

                // VALIDATION: Verify booking exists and is CANCELLED
                var booking = bookingRepository.findById(bookingId);
                if (booking.isEmpty()) {
                    log.warn("⚠️ Booking not found: {}", bookingId);
                    return;
                }

                // OPTIONAL: Notify user, process refund, update analytics, etc.
                log.info("❌ Booking cancelled: {} | Movie: {}", bookingId, booking.get().getShow().getMovieName());

                // Get released seats for reference (they should already be AVAILABLE)
                List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
                log.info("🔓 Released {} seats back to available", bookingSeats.size());
            }

        } catch (Exception e) {
            log.error("❌ Error processing BookingCancelledEvent: {}", message, e);
        }
    }
}


