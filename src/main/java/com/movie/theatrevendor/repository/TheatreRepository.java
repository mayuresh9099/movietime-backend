package com.movie.theatrevendor.repository;

import com.movie.theatrevendor.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TheatreRepository extends JpaRepository<TheatreDetails, Long> {

    List<TheatreDetails> findByStatus(TheatreStatus status);

   // List<TheatreDetails> findByCity(String city);

    List<TheatreDetails> findByCityAndStatus(String city, TheatreStatus status);

    Optional<TheatreDetails> findByOwner(Owner owner);
}
