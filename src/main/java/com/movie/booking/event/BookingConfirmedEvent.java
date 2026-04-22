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
public class BookingConfirmedEvent {
    private Long bookingId;
    private Long userId;
    private String userEmail;
    private String movieName;
    private List<String> bookedSeats;
    private Double totalPrice;
    private LocalDateTime eventTime;
}

