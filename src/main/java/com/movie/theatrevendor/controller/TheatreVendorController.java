package com.movie.theatrevendor.controller;

import com.movie.module.user.UserRepository;
import com.movie.theatrevendor.model.Owner;
import com.movie.theatrevendor.model.OwnerRequest;
import com.movie.theatrevendor.model.TheatreDetails;
import com.movie.theatrevendor.repository.OwnerRepository;
import com.movie.theatrevendor.repository.TheatreRepository;
import com.movie.theatrevendor.service.TheatreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/owner")
@RequiredArgsConstructor
public class    TheatreVendorController {

    private final TheatreRepository theatreRepository;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final TheatreService theatreService;

    @PostMapping("/theatre")
    public String createTheatre(@RequestBody TheatreDetails theatre,
                                Principal principal) {
        return theatreService.createTheatre(theatre, principal.getName());
    }

    @PostMapping("/theatre/test")
    public Owner createOwner(@RequestBody OwnerRequest request) {
        return theatreService.createOwner(request);
    }

}