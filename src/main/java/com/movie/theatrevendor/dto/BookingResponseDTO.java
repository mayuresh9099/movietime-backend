package com.movie.theatrevendor.dto;

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
public class BookingResponseDTO {
    private Long bookingId;
    private String movieName;
    private List<String> bookedSeats;
    private Integer numberOfSeats;
    private Double totalPrice;
    private String bookingStatus;
    private LocalDateTime bookingTime;
    private String message;
    private List<String> seats;
}

