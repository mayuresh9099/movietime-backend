package com.movie.theatrevendor.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ShowSeatId implements Serializable {
    private Long showId;
    private Long seatId;
}