package com.movie.theatrevendor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    @JoinColumn(name = "show_id", nullable = false, referencedColumnName = "id")
    private Show show;

    @Column(nullable = false)
    private String seatNumber; //A1,C1

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus status;
    //private String status; // AVAILABLE, LOCKED, BOOKED, CANCELLED

    // For row-level locking during booking
    @Version
    @Column(nullable = false)
    private Long version;

    // Timestamp when seat was locked (for timeout handling)
    @Column
    private LocalDateTime lockedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = SeatStatus.AVAILABLE;
        }
    }
}

