package com.movie.booking.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.booking.event.BookingCancelledEvent;
import com.movie.booking.event.BookingConfirmedEvent;
import com.movie.booking.event.BookingCreatedEvent;
import com.movie.booking.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BookingEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String BOOKING_EVENTS_TOPIC = "booking-events";
    private static final String NOTIFICATION_EVENTS_TOPIC = "notification-events";

    public BookingEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish event when booking is created (PENDING status)
     */
    public void publishBookingCreatedEvent(BookingCreatedEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(BOOKING_EVENTS_TOPIC, "booking-created", eventData);
            log.info("BookingCreatedEvent published for booking: {}", event.getBookingId());
        } catch (Exception e) {
            log.error("Error publishing BookingCreatedEvent", e);
        }
    }

    /**
     * Publish event when booking is confirmed (payment successful)
     */
    public void publishBookingConfirmedEvent(BookingConfirmedEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(BOOKING_EVENTS_TOPIC, "booking-confirmed", eventData);
            log.info("BookingConfirmedEvent published for booking: {}", event.getBookingId());
        } catch (Exception e) {
            log.error("Error publishing BookingConfirmedEvent", e);
        }
    }

    /**
     * Publish event when booking is cancelled
     */
    public void publishBookingCancelledEvent(BookingCancelledEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(BOOKING_EVENTS_TOPIC, "booking-cancelled", eventData);
            log.info("BookingCancelledEvent published for booking: {}", event.getBookingId());
        } catch (Exception e) {
            log.error("Error publishing BookingCancelledEvent", e);
        }
    }

    /**
     * Publish notification events (will be consumed by notification service)
     */
    public void publishNotificationEvent(NotificationEvent event) {
        try {
            String eventData = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(NOTIFICATION_EVENTS_TOPIC, String.valueOf(event.getUserId()), eventData);
            log.info("NotificationEvent published for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Error publishing NotificationEvent", e);
        }
    }
}

