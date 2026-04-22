package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.Owner;
import com.movie.theatrevendor.model.TheatreDetails;
import com.movie.theatrevendor.model.TheatreStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TheatreRepository extends JpaRepository<TheatreDetails, Long> {

    List<TheatreDetails> findByStatus(TheatreStatus status);

    Optional<TheatreDetails> findByOwner(Owner owner);
}

