package com.movie.theatrevendor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"show_id", "seat_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Column(nullable = false)
    private String seatNumber; // E.g., "A1", "A2", "B1", etc.

    @Column(nullable = false)
    private String status = "AVAILABLE"; // AVAILABLE, LOCKED, BOOKED, CANCELLED

    // For row-level locking during booking
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column
    private Long bookingId; // Reference to booking that owns this seat

    @PrePersist
    public void prePersist() {
        this.status = "AVAILABLE";
        this.version = 0L;
    }
}

