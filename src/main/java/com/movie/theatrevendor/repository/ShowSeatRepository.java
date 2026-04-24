package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Show;
import com.movie.theatrevendor.model.ShowSeat;
import com.movie.theatrevendor.model.ShowSeatId;
import com.movie.theatrevendor.model.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ShowSeatRepository: Handles all seat availability operations for a specific show.
 *
 * KEY DESIGN PRINCIPLES:
 * 1. PESSIMISTIC_WRITE locking prevents race conditions
 * 2. All queries work at Show level (ensuring seat isolation)
 * 3. Batch operations for performance
 * 4. Optimistic version checking for conflict detection
 */
@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, ShowSeatId> {
    /**
     * Count all locked seats across all shows (for system health)
     */
    @Query("SELECT COUNT(ss) FROM ShowSeat ss WHERE ss.status = 'LOCKED'")
    long countAllLockedSeats();

    /**
     * 🔒 CORE LOCKING METHOD: Lock available seats only if they are AVAILABLE
     * Uses SELECT FOR UPDATE to prevent double booking
     * Returns only seats that are available (not locked/booked)
     *
     * ISOLATION: SERIALIZABLE
     * This ensures no other transaction can select the same seats
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ss FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.seat.seatNumber IN :seatNumbers
            AND ss.status = com.movie.theatrevendor.model.SeatStatus.AVAILABLE
            """)
    List<ShowSeat> lockAvailableSeats(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    /**
     * Fetch seats for a booking
     */
    @Query("""
            SELECT ss FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.seat.seatNumber IN :seatNumbers
            """)
    List<ShowSeat> findByShowAndSeatNumbers(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    /**
     * Find all available seats for a show
     */
    @Query("""
            SELECT ss FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.status = com.movie.theatrevendor.model.SeatStatus.AVAILABLE
            """)
    List<ShowSeat> findAvailableSeats(@Param("showId") Long showId);

    /**
     * Count available seats for a show
     */
    @Query("""
            SELECT COUNT(ss) FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.status = 'AVAILABLE'
            """)
    Integer countAvailableSeats(@Param("showId") Long showId);

    /**
     * Batch update: Mark multiple seats as LOCKED
     * Returns number of rows updated
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ShowSeat ss
            SET ss.status = 'LOCKED',
                ss.lockedAt = :lockedAt
            WHERE ss.show.id = :showId
            AND ss.seat.seatNumber IN :seatNumbers
            AND ss.status = 'AVAILABLE'
            """)
    int lockSeats(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers,
            @Param("lockedAt") LocalDateTime lockedAt
    );

    /**
     * Batch update: Mark multiple seats as BOOKED
     * Transition: LOCKED → BOOKED
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ShowSeat ss
            SET ss.status = 'BOOKED',
                ss.lockedAt = null
            WHERE ss.show.id = :showId
            AND ss.seat.seatNumber IN :seatNumbers
            AND ss.status = 'LOCKED'
            """)
    int bookSeats(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    /**
     * Batch update: Release locked seats back to AVAILABLE
     * Used during cancellation or lock timeout
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ShowSeat ss
            SET ss.status = 'AVAILABLE',
                ss.lockedAt = null
            WHERE ss.show.id = :showId
            AND ss.seat.seatNumber IN :seatNumbers
            AND ss.status = 'LOCKED'
            """)
    int releaseSeats(
            @Param("showId") Long showId,
            @Param("seatNumbers") List<String> seatNumbers
    );

    /**
     * Find locked seats that have timed out (for expiration handling)
     * Useful for releasing locks after payment timeout
     */
    @Query("""
            SELECT ss FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.status = 'LOCKED'
            AND ss.lockedAt < :expiryTime
            """)
    List<ShowSeat> findExpiredLockedSeats(
            @Param("showId") Long showId,
            @Param("expiryTime") LocalDateTime expiryTime
    );

    /**
     * Release expired locks
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE ShowSeat ss
            SET ss.status = 'AVAILABLE',
                ss.lockedAt = null
            WHERE ss.show.id = :showId
            AND ss.status = 'LOCKED'
            AND ss.lockedAt < :expiryTime
            """)
    int releaseExpiredLocks(
            @Param("showId") Long showId,
            @Param("expiryTime") LocalDateTime expiryTime
    );

    /**
     * Get show availability summary
     */
    @Query("""
            SELECT ss.status, COUNT(ss)
            FROM ShowSeat ss
            WHERE ss.show.id = :showId
            GROUP BY ss.status
            """)
    List<Object[]> getShowSeatSummary(@Param("showId") Long showId);

    /**
     * Check if any BOOKED seats exist for a show (for validation)
     */
    @Query("""
            SELECT COUNT(ss) > 0
            FROM ShowSeat ss
            WHERE ss.show.id = :showId
            AND ss.status = 'BOOKED'
            """)
    boolean hasBookedSeats(@Param("showId") Long showId);

    /**
     * Delete all show seats for a show (when show is cancelled)
     */
    @Modifying
    @Transactional
    @Query("""
            DELETE FROM ShowSeat ss
            WHERE ss.show.id = :showId
            """)
    void deleteByShowId(@Param("showId") Long showId);

    @Modifying
    @Transactional
    @Query("""
    UPDATE ShowSeat ss
    SET ss.status = com.movie.theatrevendor.model.SeatStatus.AVAILABLE,
        ss.lockedAt = null
    WHERE ss.status = com.movie.theatrevendor.model.SeatStatus.LOCKED
    AND ss.lockedAt < :threshold
""")
    int releaseExpiredLocksForAllShows(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show.id = :showId")
    List<ShowSeat> findAllByShowId(@Param("showId") Long showId);

    @Modifying
    @Query("""
    UPDATE ShowSeat ss
    SET ss.status = 'LOCKED', ss.lockedAt = :now
    WHERE ss.show = :show
      AND ss.seat.seatNumber IN :seatNumbers
      AND ss.status = 'AVAILABLE'
""")
    int lockSeats(@Param("show") Show show,
                  @Param("seatNumbers") List<String> seatNumbers,
                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
    UPDATE ShowSeat ss
    SET ss.status = 'AVAILABLE', ss.lockedAt = null
    WHERE ss.id IN (
        SELECT bs.showSeat.id FROM BookingSeat bs WHERE bs.booking.id = :bookingId
    )
""")
    int releaseSeatsByBooking(@Param("bookingId") Long bookingId);
    @Query("""
    SELECT ss.show.id, COUNT(ss)
    FROM ShowSeat ss
    WHERE ss.show.id IN :showIds
      AND ss.status = 'AVAILABLE'
    GROUP BY ss.show.id
""")
    List<Object[]> countAvailableSeatsForShows(@Param("showIds") List<Long> showIds);

    @Query("SELECT ss FROM ShowSeat ss WHERE ss.show = :show")
    List<ShowSeat> findByShow(@Param("show") Show show);
}

