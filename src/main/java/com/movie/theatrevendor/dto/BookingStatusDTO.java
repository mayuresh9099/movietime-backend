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
public class BookingStatusDTO {
    private Long bookingId;
    private String status;

    private String movieName;
    private LocalDateTime showTime;
    private String theatre;

    private List<String> bookedSeats;
    private Double totalPrice;


    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
}

