package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Screen;
import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.model.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SeatRepository: ONLY handles physical seat definitions.
 * Seat availability per show is managed by ShowSeatRepository.
 *
 * IMPORTANT: Seat is immutable once created.
 * Status and availability are tracked in ShowSeat, not Seat.
 */
@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * Find a specific seat in a screen
     */
    Optional<Seat> findByScreenAndSeatNumber(Screen screen, String seatNumber);

    /**
     * Get all seats in a screen
     */
    List<Seat> findByScreen(Screen screen);

    /**
     * Count total seats in a screen
     */
    long countByScreen(Screen screen);

    /**
     * Find all seats for a screen (useful for show initialization)
     */
    @Query("""
            SELECT s FROM Seat s
            WHERE s.screen.id = :screenId
            ORDER BY s.seatNumber
            """)
    List<Seat> findAllSeatsInScreen(@Param("screenId") Long screenId);

    /**
     * Bulk create seats - returns number of created seats
     */
    @Query("SELECT COUNT(s) FROM Seat s WHERE s.screen.id = :screenId")
    long countSeatsInScreen(@Param("screenId") Long screenId);

    /**
     * ============================================================
     * DEPRECATED METHODS - For backward compatibility only
     * These methods throw exceptions to force refactoring.
     * DO NOT USE IN NEW CODE - Use ShowSeatRepository instead.
     * ============================================================
     */

    @Deprecated(since = "2.0", forRemoval = true)
    default java.util.Optional<Seat> findByShowAndSeatNumber(com.movie.theatrevendor.model.Show show, String seatNumber) {
        throw new UnsupportedOperationException(
                "Use ShowSeatRepository.findByShowAndSeatNumbers() instead. " +
                "Seat no longer has show relationship."
        );
    }


    @Deprecated(since = "2.0", forRemoval = true)
    default List<Seat> findByShow(com.movie.theatrevendor.model.Show show) {
        throw new UnsupportedOperationException(
                "Use ShowSeatRepository instead. Seat no longer direct reference to Show."
        );
    }


    @Deprecated(since = "2.0", forRemoval = true)
    default void deleteByShow(com.movie.theatrevendor.model.Show show) {
        throw new UnsupportedOperationException(
                "Use ShowSeatRepository.deleteByShowId(showId) instead."
        );
    }

    @Deprecated(since = "2.0", forRemoval = true)
    default long countByStatus(com.movie.theatrevendor.model.SeatStatus status) {
        throw new UnsupportedOperationException(
                "Status is now in ShowSeat, not Seat."
        );
    }

    @Deprecated(since = "2.0", forRemoval = true)
    default List<Seat> findByStatus(String status) {
        throw new UnsupportedOperationException(
                "Status is now in ShowSeat, not Seat."
        );
    }

    @Deprecated(since = "2.0", forRemoval = true)
    default List<Seat> findByStatusAndLockedAtBefore(com.movie.theatrevendor.model.SeatStatus status, LocalDateTime time) {
        throw new UnsupportedOperationException(
                "Use ShowSeatRepository.findExpiredLockedSeats() instead."
        );
    }

    @Query("""
    SELECT s FROM Seat s
    WHERE s.screen.id = :showId
    AND s.seatNumber IN :seatNumbers
""")
    List<Seat> findByShowAndSeatNumbers(Long showId, List<String> seatNumbers);
}
