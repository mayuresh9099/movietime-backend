package com.movie.theatrevendor.repository;


import com.movie.module.user.entities.User;
import com.movie.theatrevendor.model.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByUser(User user);
    //Optional<TheatreDetails> findByOwner(Owner owner);
    Optional<Owner> findByUserId(Long userId); //
}