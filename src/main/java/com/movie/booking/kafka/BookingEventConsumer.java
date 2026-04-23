package com.movie.booking.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.theatrevendor.model.BookingSeat;
import com.movie.theatrevendor.model.SeatStatus;
import com.movie.theatrevendor.model.Booking;
import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.repository.BookingRepository;
import com.movie.theatrevendor.repository.BookingSeatRepository;
import com.movie.theatrevendor.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BookingEventConsumer {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;
    private final BookingSeatRepository bookingSeatRepository;

    public BookingEventConsumer(SeatRepository seatRepository,
                                BookingRepository bookingRepository,
                                ObjectMapper objectMapper, BookingSeatRepository bookingSeatRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    /**
     * Consume BookingConfirmedEvent and update seat availability
     */
    @KafkaListener(topics = "booking-events", groupId = "booking-group")
    @Transactional
    public void handleBookingConfirmed(String message) {
        try {
            if (message.contains("booking-confirmed")) {

                BookingConfirmedEvent event =
                        objectMapper.readValue(message, BookingConfirmedEvent.class);

                Long bookingId = event.getBookingId();

                // Get seats via mapping table
                List<BookingSeat> bookingSeats =
                        bookingSeatRepository.findByBookingId(bookingId);

                List<Seat> seatsToUpdate = bookingSeats.stream()
                        .map(BookingSeat::getSeat)
                        .toList();

                // Update status
                for (Seat seat : seatsToUpdate) {
                    seat.setStatus(SeatStatus.BOOKED);
                    seat.setLockedAt(null); // optional cleanup
                }

                // Save in batch
                seatRepository.saveAll(seatsToUpdate);

                log.info("Seats marked as BOOKED for booking: {}", bookingId);
            }

        } catch (Exception e) {
            log.error("Error processing BookingConfirmedEvent: {}", message, e);
        }
    }

    @KafkaListener(topics = "booking-events", groupId = "booking-cancellation-group")
    @Transactional
    public void handleBookingCancelled(String message) {
        try {
            if (message.contains("booking-cancelled")) {

                BookingCancelledEvent event =
                        objectMapper.readValue(message, BookingCancelledEvent.class);

                Long bookingId = event.getBookingId();

                // Get seats via mapping table
                List<BookingSeat> bookingSeats =
                        bookingSeatRepository.findByBookingId(bookingId);

                List<Seat> seats = bookingSeats.stream()
                        .map(BookingSeat::getSeat)
                        .toList();

                // Update seats
                for (Seat seat : seats) {
                    seat.setStatus(SeatStatus.AVAILABLE);
                    seat.setLockedAt(null);
                }

                // Batch save
                seatRepository.saveAll(seats);

                // Remove mapping
                bookingSeatRepository.deleteByBookingId(bookingId);

                log.info("Seats released for booking cancellation: {}", bookingId);
            }

        } catch (Exception e) {
            log.error("Error processing BookingCancelledEvent: {}", message, e);
        }
    }
}


