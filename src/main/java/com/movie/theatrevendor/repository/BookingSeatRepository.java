package com.movie.theatrevendor.repository;

import com.movie.module.user.entities.User;
import com.movie.theatrevendor.model.Booking;
import com.movie.theatrevendor.model.BookingSeat;
import com.movie.theatrevendor.model.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * BookingSeatRepository: Maps bookings to show seats.
 * This is used for booking history and seat lookup.
 *
 * DESIGN:
 * - Booking → BookingSeat (1:Many)
 * - BookingSeat → ShowSeat (Many:1)
 * - ShowSeat contains the actual status (BOOKED)
 */
@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /**
     * Find all booking seats for a booking
     */
    List<BookingSeat> findByBooking(Booking booking);

    /**
     * Find seats (seat numbers) for a specific booking
     */
    @Query("""
            SELECT bs.showSeat.seat.seatNumber
            FROM BookingSeat bs
            WHERE bs.booking.id = :bookingId
            """)
    List<String> findSeatNumbersByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Find booking seat mappings by booking ID
     */
    @Query("""
            SELECT bs FROM BookingSeat bs
            WHERE bs.booking.id = :bookingId
            """)
    List<BookingSeat> findByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Find seats for all bookings by a user (with booking history)
     * Returns: [booking_id, seat_number]
     */
    @Query("""
            SELECT bs.booking.id, bs.showSeat.seat.seatNumber
            FROM BookingSeat bs
            WHERE bs.booking.user = :user
            """)
    List<Object[]> findSeatNumbersByUser(@Param("user") User user);

    /**
     * Delete all booking seat mappings for a booking
     * Called when cancelling a booking
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM BookingSeat bs WHERE bs.booking.id = :bookingId")
    int deleteByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Check if a show has any bookings
     */
    @Query("""
            SELECT COUNT(bs) > 0
            FROM BookingSeat bs
            WHERE bs.showSeat.show.id = :showId
            """)
    boolean existsByShowId(@Param("showId") Long showId);

    /**
     * Count total bookings for a show
     */
    @Query("""
            SELECT COUNT(DISTINCT bs.booking.id)
            FROM BookingSeat bs
            WHERE bs.showSeat.show.id = :showId
            """)
    long countBookingsForShow(@Param("showId") Long showId);

    /**
     * Find seat numbers already booked in a show
     */
    @Query("""
            SELECT DISTINCT bs.showSeat.seat.seatNumber
            FROM BookingSeat bs
            WHERE bs.showSeat.show.id = :showId
            """)
    List<String> findBookedSeatNumbersInShow(@Param("showId") Long showId);

    @Query("SELECT bs.showSeat FROM BookingSeat bs WHERE bs.booking = :booking")
    List<ShowSeat> findShowSeatsByBooking(@Param("booking") Booking booking);

    @Query("""
    SELECT COUNT(bs)
    FROM BookingSeat bs
    WHERE bs.booking.show.id = :showId
    AND bs.showSeat.seat.id IN :seatIds
""")
    int countBookedSeats(@Param("showId") Long showId,
                         @Param("seatIds") List<Long> seatIds);


}



