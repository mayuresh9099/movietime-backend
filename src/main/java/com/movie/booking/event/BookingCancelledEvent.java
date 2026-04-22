package com.movie.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingCancelledEvent {
    private Long bookingId;
    private Long userId;
    private String userEmail;
    private List<String> cancelledSeats;
    private Double refundAmount;
    private LocalDateTime eventTime;
    private String cancellationReason;
}

