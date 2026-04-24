package com.movie.booking.service;

import com.movie.module.user.UserRepository;
import com.movie.module.user.entities.User;
import com.movie.theatrevendor.dto.CreateShowRequest;
import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowManagementService {

    private final ShowRepository showRepository;
    private final TheatreRepository theatreRepository;
    private final SeatRepository seatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final ScreenRepository screenRepository;

    // ================= CREATE SHOW =================

    @Transactional
    public Show createShow(CreateShowRequest request, String ownerEmail) {

        validateOwnerAccess(request.getTheatreId(), ownerEmail);

        TheatreDetails theatre = theatreRepository.findById(request.getTheatreId())
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        Screen screen = screenRepository.findById(request.getScreenId())
                .orElseThrow(() -> new RuntimeException("Screen not found"));

        if (!screen.getTheatre().getId().equals(theatre.getId())) {
            throw new RuntimeException("Screen does not belong to this theatre");
        }

        if (theatre.getStatus() != TheatreStatus.APPROVED) {
            throw new RuntimeException("Theatre not approved");
        }
// ✅ Validate shows can span at most 2 calendar days (allows midnight shows like 9 PM → 12:30 AM)
        if (request.getEndTime().toLocalDate().isAfter(request.getStartTime().toLocalDate().plusDays(1))) {
            throw new RuntimeException("Show cannot span more than 2 calendar days");
        }

        // ✅ Validate max show duration (4 hours) - prevents unrealistic overnight shows
        if (java.time.temporal.ChronoUnit.MINUTES.between(request.getStartTime(), request.getEndTime()) > 240) {
            throw new RuntimeException("Show duration cannot exceed 4 hours");
        }
        boolean exists = showRepository.existsByScreenAndTimeOverlap(
                screen.getId(),
                request.getStartTime(),
                request.getEndTime()
        );

        if (exists) {
            throw new RuntimeException("Show timing overlaps");
        }

        Show show = Show.builder()
                .theatre(theatre)
                .screen(screen)
                .movieName(request.getMovieName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .totalSeats(request.getTotalSeats())
                .availableSeats(request.getTotalSeats())
                .pricePerSeat(request.getPricePerSeat())
                .status(ShowStatus.ACTIVE)
                .build();

        Show savedShow = showRepository.save(show);

        // ✅ STEP 1: Create seats ONLY once per screen
        if (seatRepository.countByScreen(screen) == 0) {
            createSeatsForScreen(screen);
        }

        // ✅ STEP 2: Create ShowSeat mapping (CRITICAL)
        createShowSeats(savedShow);

        return savedShow;
    }

    // ================= DELETE SHOW =================

    @Transactional
    public void deleteShow(Long showId, String ownerEmail) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        validateOwnerAccess(show.getTheatre().getId(), ownerEmail);

        if (show.getStartTime().isBefore(java.time.LocalDateTime.now())) {
            throw new RuntimeException("Cannot delete started show");
        }

        // ✅ Delete ShowSeat (NOT Seat)
        showSeatRepository.deleteByShowId(showId);

        showRepository.delete(show);
    }

    // ================= HELPERS =================

    private void createSeatsForScreen(Screen screen) {

        int totalSeats = screen.getTotalSeats();
        int seatsPerRow = 10;
        int count = 0;

        for (int i = 0; count < totalSeats; i++) {
            char row = (char) ('A' + i);

            for (int j = 1; j <= seatsPerRow && count < totalSeats; j++) {
                Seat seat = new Seat();
                seat.setScreen(screen);
                seat.setSeatNumber(row + String.valueOf(j));
                seatRepository.save(seat);
                count++;
            }
        }

        log.info("✅ Seats created for screen {}", screen.getId());
    }

    private void createShowSeats(Show show) {

        List<Seat> seats = seatRepository.findByScreen(show.getScreen());

        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(SeatStatus.AVAILABLE)
                        .build())
                .toList();

        showSeatRepository.saveAll(showSeats);

        log.info("✅ ShowSeats created for show {}", show.getId());
    }

    private void validateOwnerAccess(Long theatreId, String ownerEmail) {

        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Owner owner = ownerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        TheatreDetails theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        if (!theatre.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Unauthorized");
        }
    }

    @Transactional
    public Show updateShow(Long showId, CreateShowRequest request, String ownerEmail) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        validateOwnerAccess(show.getTheatre().getId(), ownerEmail);

        // 🚨 CRITICAL: Prevent update if any seats are BOOKED
        boolean hasBookings = showSeatRepository.hasBookedSeats(showId);
        if (hasBookings) {
            throw new RuntimeException("Cannot update show with existing bookings");
        }

        // 🚨 Prevent update if show already started
        if (show.getStartTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot update started show");
        }

        // ✅ Validate new screen
        Screen screen = screenRepository.findById(request.getScreenId())
                .orElseThrow(() -> new RuntimeException("Screen not found"));

        if (!screen.getTheatre().getId().equals(show.getTheatre().getId())) {
            throw new RuntimeException("Screen mismatch");
        }

        // ✅ Check time overlap
        boolean exists = showRepository.existsByScreenAndTimeOverlap(
                screen.getId(),
                request.getStartTime(),
                request.getEndTime()
        );

        if (exists) {
            throw new RuntimeException("Show timing overlaps");
        }

        // ✅ Update fields
        show.setMovieName(request.getMovieName());
        show.setStartTime(request.getStartTime());
        show.setEndTime(request.getEndTime());
        show.setPricePerSeat(request.getPricePerSeat());
        show.setScreen(screen);

        return showRepository.save(show);
    }

    public List<Show> getTheatreShows(String ownerEmail) {

        User user = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Owner owner = ownerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        return showRepository.findByTheatreOwner(owner);
    }

    @Transactional
    public void createSeatsForExistingShow(Long showId, String ownerEmail) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        validateOwnerAccess(show.getTheatre().getId(), ownerEmail);

        // ✅ Step 1: Check if ShowSeats already exist
        List<ShowSeat> existing = showSeatRepository.findAvailableSeats(showId);
        if (!existing.isEmpty()) {
            throw new RuntimeException("Show seats already created for this show");
        }

        // ✅ Step 2: Get all seats from screen
        Screen screen = show.getScreen();

        List<Seat> seats = seatRepository.findByScreen(screen);
        if (seats.isEmpty()) {
            throw new RuntimeException("No seats found for this screen. Populate screen seats first.");
        }

        // ✅ Step 3: Create ShowSeat for each seat
        List<ShowSeat> showSeats = seats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(SeatStatus.AVAILABLE)
                        .lockedAt(null)
                        .build()
                )
                .toList();

        showSeatRepository.saveAll(showSeats);

        // ✅ Step 4: Update available seats count
        show.setAvailableSeats(showSeats.size());
        showRepository.save(show);

        log.info("✅ Created {} ShowSeats for show {}", showSeats.size(), showId);
    }
}