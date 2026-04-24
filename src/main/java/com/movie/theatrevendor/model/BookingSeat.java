package com.movie.theatrevendor.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * BookingSeat maps a specific booking to a ShowSeat.
 * This allows us to track which seats are booked for a specific show.
 * <p>
 * DESIGN:
 * - Maps booking to show seats
 * - Created once when booking is confirmed
 * - Used for history and reporting
 */
@Entity
@Table(name = "booking_seats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "show_seat_id")
    private ShowSeat showSeat;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
