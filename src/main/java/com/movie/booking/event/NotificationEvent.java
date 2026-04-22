package com.movie.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {
    private Long userId;
    private String userEmail;
    private String messageType; // BOOKING_CONFIRMED, BOOKING_FAILED, BOOKING_CANCELLED
    private String message;
    private LocalDateTime eventTime;
}

