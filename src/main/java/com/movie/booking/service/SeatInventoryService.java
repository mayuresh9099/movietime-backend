package com.movie.booking.service;

import com.movie.theatrevendor.model.SeatStatus;
import com.movie.theatrevendor.model.Seat;
import com.movie.theatrevendor.model.Show;
import com.movie.theatrevendor.model.ShowSeat;
import com.movie.theatrevendor.repository.SeatRepository;
import com.movie.theatrevendor.repository.ShowRepository;
import com.movie.theatrevendor.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatInventoryService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    /**
     * Allocate seats for a show (creates ShowSeat from Screen seats)
     */
    @Transactional
    public void allocateSeatsForShow(Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        // ✅ Check if already allocated
        Integer existing = showSeatRepository.countAvailableSeats(showId);
        if (existing != null && existing > 0) {
            throw new RuntimeException("Seats already allocated for this show");
        }

        // ✅ Get physical seats from screen
        List<Seat> screenSeats = seatRepository.findByScreen(show.getScreen());

        if (screenSeats.isEmpty()) {
            throw new RuntimeException("No seats defined for this screen");
        }

        // ✅ Create ShowSeat entries
        List<ShowSeat> showSeats = screenSeats.stream()
                .map(seat -> ShowSeat.builder()
                        .show(show)
                        .seat(seat)
                        .status(SeatStatus.AVAILABLE)
                        .lockedAt(null)
                        .build())
                .toList();

        showSeatRepository.saveAll(showSeats);

        // ✅ Update counters
        show.setTotalSeats(showSeats.size());
        show.setAvailableSeats(showSeats.size());
        showRepository.save(show);

        log.info("✅ Allocated {} seats for show {}", showSeats.size(), showId);
    }


    /**
     * Update seat availability (LOCK / RELEASE)
     */
    @Transactional
    public void updateSeatAvailability(Long showId, List<String> seatNumbers, SeatStatus newStatus) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        List<ShowSeat> showSeats = showSeatRepository
                .findByShowAndSeatNumbers(showId, seatNumbers);

        if (showSeats.size() != seatNumbers.size()) {
            throw new RuntimeException("Some seats not found");
        }

        // ❌ prevent modification of booked seats
        for (ShowSeat ss : showSeats) {
            if (ss.getStatus() == SeatStatus.BOOKED) {
                throw new RuntimeException(
                        "Cannot modify booked seat: " + ss.getSeat().getSeatNumber()
                );
            }
        }

        int updated;

        if (newStatus == SeatStatus.LOCKED) {
            updated = showSeatRepository.lockSeats(showId, seatNumbers, LocalDateTime.now());
        } else if (newStatus == SeatStatus.AVAILABLE) {
            updated = showSeatRepository.releaseSeats(showId, seatNumbers);
        } else {
            throw new RuntimeException("Unsupported status update");
        }

        if (updated != seatNumbers.size()) {
            log.warn("⚠️ Some seats were not updated properly");
        }

        // ✅ recalc availability
        int available = showSeatRepository.countAvailableSeats(showId);
        show.setAvailableSeats(available);
        showRepository.save(show);

        log.info("✅ Updated {} seats to {} for show {}", seatNumbers.size(), newStatus, showId);
    }

    /**
     * Block seats for maintenance
     */
    @Transactional
    public void blockSeatsForMaintenance(Long showId, List<String> seatNumbers) {
        updateSeatAvailability(showId, seatNumbers, SeatStatus.LOCKED);
        log.info("🔒 Blocked seats: {}", String.join(", ", seatNumbers));
    }

    /**
     * Unblock seats from maintenance
     */
    @Transactional
    public void unblockSeatsFromMaintenance(Long showId, List<String> seatNumbers) {
        updateSeatAvailability(showId, seatNumbers, SeatStatus.AVAILABLE);
        log.info("🔓 Unblocked seats: {}", String.join(", ", seatNumbers));
    }

    /**
     * Get inventory status
     */
    public SeatInventoryStatus getSeatInventoryStatus(Long showId) {

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new RuntimeException("Show not found"));

        List<Object[]> summary = showSeatRepository.getShowSeatSummary(showId);

        long available = 0, booked = 0, locked = 0;

        for (Object[] row : summary) {
            SeatStatus status = (SeatStatus) row[0];
            long count = (Long) row[1];

            switch (status) {
                case AVAILABLE -> available = count;
                case BOOKED -> booked = count;
                case LOCKED -> locked = count;
            }
        }

        List<String> availableSeats = showSeatRepository.findAvailableSeats(showId)
                .stream()
                .map(ss -> ss.getSeat().getSeatNumber())
                .sorted()
                .toList();

        return new SeatInventoryStatus(
                show.getTotalSeats(),
                available,
                booked,
                locked,
                availableSeats
        );
    }

    /**
     * Get full seat map (ALL seats, not just available)
     */
    public List<Map<String, Object>> getSeatMap(Long showId) {

        List<ShowSeat> seats = showSeatRepository.findAllByShowId(showId);

        Map<Character, List<ShowSeat>> grouped = seats.stream()
                .collect(Collectors.groupingBy(ss -> ss.getSeat().getSeatNumber().charAt(0)));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {

                    List<Map<String, Object>> rowSeats = entry.getValue().stream()
                            .sorted(Comparator.comparing(
                                    ss -> Integer.parseInt(ss.getSeat().getSeatNumber().substring(1))
                            ))
                            .map(ss -> Map.<String, Object>of(
                                    "number", Integer.parseInt(ss.getSeat().getSeatNumber().substring(1)),
                                    "status", ss.getStatus().name()
                            ))
                            .toList();

                    return Map.of(
                            "row", entry.getKey().toString(),
                            "seats", rowSeats
                    );
                })
                .toList();
    }

    /**
     * DTO
     */
    public static class SeatInventoryStatus {

        private final int totalSeats;
        private final long availableSeats;
        private final long bookedSeats;
        private final long lockedSeats;
        private final List<String> availableSeatNumbers;

        public SeatInventoryStatus(int totalSeats, long availableSeats,
                                   long bookedSeats, long lockedSeats,
                                   List<String> availableSeatNumbers) {
            this.totalSeats = totalSeats;
            this.availableSeats = availableSeats;
            this.bookedSeats = bookedSeats;
            this.lockedSeats = lockedSeats;
            this.availableSeatNumbers = availableSeatNumbers;
        }

        public int getTotalSeats() { return totalSeats; }
        public long getAvailableSeats() { return availableSeats; }
        public long getBookedSeats() { return bookedSeats; }
        public long getLockedSeats() { return lockedSeats; }
        public List<String> getAvailableSeatNumbers() { return availableSeatNumbers; }
    }
}
