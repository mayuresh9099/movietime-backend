package com.movie.booking.service;

import com.movie.theatrevendor.model.Screen;
import com.movie.theatrevendor.model.ScreenStatus;
import com.movie.theatrevendor.model.ScreenType;
import com.movie.theatrevendor.model.TheatreDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScreenRepository extends JpaRepository<Screen, Long> {

    Optional<Screen> findByIdAndTheatreId(Long screenId, Long theatreId);
}