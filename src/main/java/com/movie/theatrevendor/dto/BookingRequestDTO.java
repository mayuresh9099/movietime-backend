package com.movie.theatrevendor.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "Show ID is required")
    private Long showId;

    @NotEmpty(message = "At least one seat must be selected")
    @Size(max = 10, message = "Maximum 10 seats allowed")
    private List<
            @Pattern(regexp = "^[A-Z][0-9]+$", message = "Invalid seat format. Example: A1, B2, C10")
                    String
            > seatNumbers;
}
