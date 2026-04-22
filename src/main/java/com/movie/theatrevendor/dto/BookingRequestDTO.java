package com.movie.theatrevendor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRequestDTO {
    private Long showId;
    private List<String> seatNumbers; // E.g., ["A1", "A2", "B1"]
}

