package com.movie.admin.controller.service;

import com.movie.theatrevendor.model.Owner;
import com.movie.theatrevendor.model.TheatreDetails;
import com.movie.theatrevendor.model.TheatreStatus;
import com.movie.theatrevendor.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final TheatreRepository theatreRepository;

    public List<TheatreDetails> getPendingTheatres() {
        return theatreRepository.findByStatus(TheatreStatus.PENDING);
    }

/*    public String approveTheatre(Long theatreId) {

        TheatreDetails theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        if ("APPROVED".equals(theatre.getStatus())) {
            return "Already approved";
        }

        theatre.setStatus("APPROVED");
        theatreRepository.save(theatre);

        return "Theatre approved successfully";
    }*/

    public String approveTheatre(Long theatreId) {

        TheatreDetails theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        if (theatre.getStatus() == TheatreStatus.APPROVED) {
            return "Already approved";
        }

        // 🔥 Optional but important validation
        Owner owner = theatre.getOwner();
        if (owner == null) {
            throw new RuntimeException("Owner not linked to theatre");
        }

        // Optional: check if owner is verified
        if (!Boolean.TRUE.equals(owner.getIsVerified())) {
            throw new RuntimeException("Owner is not verified");
        }

        theatre.setStatus(TheatreStatus.APPROVED);

        theatreRepository.save(theatre);

        return "Theatre approved successfully";
    }

    public String rejectTheatre(Long theatreId) {

        TheatreDetails theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        theatre.setStatus(TheatreStatus.REJECTED);
        theatreRepository.save(theatre);

        return "Theatre rejected";
    }
}