package com.movie.theatrevendor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Seat represents a physical seat in a screen.
 * It is NOT tied to a specific show - seat availability per show is tracked in ShowSeat.
 *
 * DESIGN PRINCIPLE:
 * Seat is immutable once created. Status is NOT stored here.
 * Status is stored in ShowSeat (per show availability).
 */
@Entity
@Table(
        name = "seats",
        indexes = {
                @Index(name = "idx_seat_screen", columnList = "screen_id"),
                @Index(name = "idx_seat_number", columnList = "seat_number")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"screen_id", "seat_number"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class  Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(nullable = false)
    private String seatNumber; // A1, A2, B1, etc.

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}


