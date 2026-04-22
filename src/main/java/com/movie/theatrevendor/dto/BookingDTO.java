package com.movie.theatrevendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ShowDTO {
    private Long id;
    private String movieName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSeats;
    private Integer availableSeats;
    private Double pricePerSeat;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SeatDTO {
    private Long id;
    private String seatNumber;
    private String status;
}



