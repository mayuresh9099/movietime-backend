package com.movie.booking.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.common.util.model.SeatStatus;
import com.movie.theatrevendor.model.Booking;
import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class BookingEventConsumer {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    public BookingEventConsumer(SeatRepository seatRepository,
                                BookingRepository bookingRepository,
                                ObjectMapper objectMapper) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Consume BookingConfirmedEvent and update seat availability
     */
    @KafkaListener(topics = "booking-events", groupId = "booking-group")
    @Transactional
    public void handleBookingConfirmed(String message) {
        try {
            // Check if this is a confirmed event
            if (message.contains("booking-confirmed")) {
                BookingConfirmedEvent event = objectMapper.readValue(message, BookingConfirmedEvent.class);

                // Mark all seats as BOOKED
                Optional<Booking> booking = bookingRepository.findById(event.getBookingId());
                if (booking.isPresent()) {
                    for (Seat seat : booking.get().getSeats()) {
                        seat.setStatus(SeatStatus.BOOKED);
                        seat.setBookingId(event.getBookingId());
                        seatRepository.save(seat);
                    }
                    log.info("Seats marked as BOOKED for booking: {}", event.getBookingId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing BookingConfirmedEvent: {}", message, e);
        }
    }

    /**
     * Consume BookingCancelledEvent and release seats
     */
    @KafkaListener(topics = "booking-events", groupId = "booking-cancellation-group")
    @Transactional
    public void handleBookingCancelled(String message) {
        try {
            if (message.contains("booking-cancelled")) {
                BookingCancelledEvent event = objectMapper.readValue(message, BookingCancelledEvent.class);

                // Mark all seats as AVAILABLE again
                Optional<Booking> booking = bookingRepository.findById(event.getBookingId());
                if (booking.isPresent()) {
                    for (Seat seat : booking.get().getSeats()) {
                        seat.setStatus(SeatStatus.AVAILABLE);
                        seat.setBookingId(null);
                        seatRepository.save(seat);
                    }
                    log.info("Seats marked as AVAILABLE for booking cancellation: {}", event.getBookingId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing BookingCancelledEvent: {}", message, e);
        }
    }
}


