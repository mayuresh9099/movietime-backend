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
public class BookingCreatedEvent {
    private Long bookingId;
    private Long userId;
    private Long showId;
    private String movieName;
    private List<String> bookedSeats;
    private Integer numberOfSeats;
    private Double totalPrice;
    private String bookingStatus;
    private LocalDateTime eventTime;
}

