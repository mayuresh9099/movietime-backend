package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {


    List<Show> findByTheatreOwner(Owner owner);

    @Query("SELECT s FROM Show s WHERE s.theatre = ?1 AND s.startTime BETWEEN ?2 AND ?3")
    List<Show> findShowsBetweenDates(TheatreDetails theatre, LocalDateTime startTime, LocalDateTime endTime);

    @Query("""
                SELECT COUNT(s) > 0
                FROM Show s
                WHERE s.screen.id = :screenId
                AND s.startTime < :endTime
                AND s.endTime > :startTime
            """)
    boolean existsByScreenIdAndTimeOverlap(
            @Param("screenId") Long screenId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
            SELECT s FROM Show s
            WHERE s.theatre IN :theatres
            AND s.status = :status
            AND LOWER(s.movieName) LIKE LOWER(CONCAT('%', :movieName, '%'))
            """)
    List<Show> findShowsByTheatresAndMovie(List<TheatreDetails> theatres,
                                           ShowStatus status,
                                           String movieName);

/*    @Query("""
                SELECT COUNT(s) > 0 FROM Show s
                WHERE s.screen = :screen
                AND (
                    (:startTime BETWEEN s.startTime AND s.endTime)
                    OR (:endTime BETWEEN s.startTime AND s.endTime)
                    OR (s.startTime BETWEEN :startTime AND :endTime)
                )
            """)
    boolean existsByScreenAndTimeOverlap(
            @Param("screen") Screen screen,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );*/



    @Query("""
                SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
                FROM Show s
                WHERE s.screen.id = :screenId
                AND s.status = 'ACTIVE'
                AND (
                    :startTime < s.endTime AND :endTime > s.startTime
                )
            """)
    boolean existsByScreenAndTimeOverlap(
            @Param("screenId") Long screenId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}

