package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Show;
import com.movie.theatrevendor.model.TheatreDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByTheatre(TheatreDetails theatre);

    List<Show> findByTheatreAndStatus(TheatreDetails theatre, String status);

    @Query("SELECT s FROM Show s WHERE s.theatre = ?1 AND s.startTime BETWEEN ?2 AND ?3")
    List<Show> findShowsBetweenDates(TheatreDetails theatre, LocalDateTime startTime, LocalDateTime endTime);

    @Query("""
    SELECT COUNT(s) > 0
    FROM Show s
    WHERE s.screenId = :screenId
    AND s.startTime < :endTime
    AND s.endTime > :startTime
""")
    boolean existsByScreenIdAndTimeOverlap(
            @Param("screenId") Long screenId,           // ✅ FIXED
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}

