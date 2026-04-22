package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.model.Show;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    Optional<Seat> findByShowAndSeatNumber(Show show, String seatNumber);

    List<Seat> findByShowAndStatus(Show show, String status);

    // Pessimistic locking: locks selected rows in database
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.show = ?1 AND s.seatNumber IN ?2 AND s.status = 'AVAILABLE'")
    List<Seat> findAvailableSeatsForBooking(Show show, List<String> seatNumbers);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.show = ?1 AND s.status = 'AVAILABLE'")
    Integer countAvailableSeats(Show show);
}

