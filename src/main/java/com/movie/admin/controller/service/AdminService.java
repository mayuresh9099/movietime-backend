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
    public String approveTheatre(Long theatreId) {

        // Debug: Check if theatre exists
        boolean exists = theatreRepository.existsById(theatreId);
        if (!exists) {
            throw new RuntimeException("Theatre with ID " + theatreId + " does not exist in database");
        }

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

    // Debug method to check all theatres
    public List<TheatreDetails> getAllTheatres() {
        return theatreRepository.findAll();
    }

    // Debug methods for database status
    public long getUserCount() {
        // This would need UserRepository injection, but for now return 0
        return 0;
    }

    public long getOwnerCount() {
        // This would need OwnerRepository injection, but for now return 0
        return 0;
    }

    public long getTheatreCount() {
        return theatreRepository.count();
    }
}