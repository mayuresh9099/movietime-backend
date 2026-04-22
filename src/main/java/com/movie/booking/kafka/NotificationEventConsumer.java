package com.movie.booking.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.booking.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Mock Notification Service Consumer
 * In production, this would send emails, SMS, or in-app notifications
 */
@Slf4j
@Service
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Consume notification events and send notifications
     * This is a mock implementation - in real scenario, integrate with email/SMS service
     */
    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void handleNotificationEvent(String message) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);

            // Mock notification logic
            switch (event.getMessageType()) {
                case "BOOKING_CONFIRMED":
                    sendBookingConfirmationEmail(event);
                    break;
                case "BOOKING_FAILED":
                    sendBookingFailureEmail(event);
                    break;
                case "BOOKING_CANCELLED":
                    sendCancellationEmail(event);
                    break;
                default:
                    log.warn("Unknown notification type: {}", event.getMessageType());
            }

            log.info("Notification sent to user: {} with message type: {}",
                    event.getUserEmail(), event.getMessageType());
        } catch (Exception e) {
            log.error("Error processing NotificationEvent: {}", message, e);
        }
    }

    private void sendBookingConfirmationEmail(NotificationEvent event) {
        // Mock email sending
        log.info("📧 [BOOKING CONFIRMED EMAIL] To: {} | {}", event.getUserEmail(), event.getMessage());
        // TODO: Integrate with actual email service (JavaMailSender, SendGrid, AWS SES)
    }

    private void sendBookingFailureEmail(NotificationEvent event) {
        // Mock email sending
        log.info("📧 [BOOKING FAILED EMAIL] To: {} | {}", event.getUserEmail(), event.getMessage());
    }

    private void sendCancellationEmail(NotificationEvent event) {
        // Mock email sending
        log.info("📧 [BOOKING CANCELLED EMAIL] To: {} | {}", event.getUserEmail(), event.getMessage());
    }
}

