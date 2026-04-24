package com.movie.theatrevendor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ShowSeat represents the seat availability for a specific show.
 * This is the bridge between physical seats (Seat) and shows.
 *
 * DESIGN:
 * - Seat: Physical seat in a screen (e.g., Screen 1, Seat A1)
 * - ShowSeat: Availability tracking for that seat in a specific show
 * - Status: AVAILABLE, LOCKED, BOOKED
 * - Version: Optimistic locking for concurrent updates
 */
@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"show_id", "seat_id"})
})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShowSeat {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Show show;

    @ManyToOne
    private Seat seat;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private LocalDateTime lockedAt;

    @Version
    private Long version;

}

