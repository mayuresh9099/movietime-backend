package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Booking;
import com.movie.module.user.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findById(Long id);

    List<Booking> findByUser(User user);

    List<Booking> findByBookingStatus(String status);

    @Query("SELECT b FROM Booking b WHERE b.bookingStatus = 'PENDING' AND b.createdAt < :expiryTime")
    List<Booking> findExpiredPendingBookings(@Param("expiryTime") LocalDateTime expiryTime);

    List<Booking> findByUserAndBookingStatus(User user, String status);
}

