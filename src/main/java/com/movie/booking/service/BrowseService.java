package com.movie.booking.service;

import com.movie.theatrevendor.model.*;
import com.movie.theatrevendor.repository.ShowRepository;
import com.movie.theatrevendor.repository.ShowSeatRepository;
import com.movie.theatrevendor.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrowseService {

    private final TheatreRepository theatreRepository;
    private final ShowRepository showRepository;
    private final ShowSeatRepository showSeatRepository;

    /**
     * Browse theatres showing a specific movie in a city
     */
    public List<Map<String, Object>> browseTheatresByMovieAndCity(String movieName, String cityName) {

        // ✅ Step 1: Get theatres
        List<TheatreDetails> theatres = theatreRepository
                .findByCityAndStatus(cityName, TheatreStatus.APPROVED);

        if (theatres.isEmpty()) return List.of();

        // ✅ Step 2: Get all shows in ONE query
        List<Show> shows = showRepository
                .findShowsByTheatresAndMovie(theatres, ShowStatus.ACTIVE, movieName);

        if (shows.isEmpty()) return List.of();

        // ✅ Step 3: Get available seat count (SHOW SEAT BASED)
        Map<Long, Long> seatCountMap = showSeatRepository
                .countAvailableSeatsForShows(
                        shows.stream().map(Show::getId).toList()
                )
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],   // showId
                        row -> (Long) row[1]    // count
                ));

        // ✅ Step 4: Group shows by theatre
        Map<TheatreDetails, List<Show>> showsByTheatre =
                shows.stream().collect(Collectors.groupingBy(Show::getTheatre));

        // ✅ Step 5: Build response
        return theatres.stream()
                .map(theatre -> {

                    List<Show> theatreShows = showsByTheatre.getOrDefault(theatre, List.of());

                    List<Map<String, Object>> movieShows = theatreShows.stream()
                            .map(show -> Map.<String, Object>of(
                                    "showId", show.getId(),
                                    "startTime", show.getStartTime(),
                                    "endTime", show.getEndTime(),
                                    "pricePerSeat", show.getPricePerSeat(),
                                    "availableSeats", seatCountMap.getOrDefault(show.getId(), 0L)
                            ))
                            .toList();

                    if (movieShows.isEmpty()) return null;

                    return Map.<String, Object>of(
                            "theatreId", theatre.getId(),
                            "theatreName", theatre.getName(),
                            "address", theatre.getAddress(),
                            "totalScreens", theatre.getTotalScreens(),
                            "shows", movieShows
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get show timings for a specific date
     */
    public List<Map<String, Object>> getShowTimingsForDate(Long theatreId, LocalDate date) {

        TheatreDetails theatre = theatreRepository.findById(theatreId)
                .orElseThrow(() -> new RuntimeException("Theatre not found"));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        List<Show> shows = showRepository
                .findShowsBetweenDates(theatre, startOfDay, endOfDay);

        return shows.stream()
                .filter(show -> show.getStatus() == ShowStatus.ACTIVE)
                .map(show -> {

                    // ✅ Fetch ShowSeats (NOT Seat)
                    List<ShowSeat> showSeats = showSeatRepository.findByShow(show);

                    // ✅ Available count
                    int availableSeats = (int) showSeats.stream()
                            .filter(ss -> ss.getStatus() == SeatStatus.AVAILABLE)
                            .count();

                    // ✅ Seat map
                    List<Map<String, Object>> seatMap = buildSeatMap(showSeats);

                    return Map.<String, Object>of(
                            "showId", show.getId(),
                            "movieName", show.getMovieName(),
                            "startTime", show.getStartTime(),
                            "endTime", show.getEndTime(),
                            "pricePerSeat", show.getPricePerSeat(),
                            "availableSeats", availableSeats,
                            "seatMap", seatMap
                    );
                })
                .toList();
    }

    /**
     * Build seat map (ROW-WISE)
     */
    private List<Map<String, Object>> buildSeatMap(List<ShowSeat> showSeats) {

        Map<Character, List<ShowSeat>> seatsByRow = showSeats.stream()
                .collect(Collectors.groupingBy(
                        ss -> ss.getSeat().getSeatNumber().charAt(0)
                ));

        return seatsByRow.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {

                    Character row = entry.getKey();

                    List<Map<String, Object>> seatList = entry.getValue().stream()
                            .sorted(Comparator.comparing(
                                    ss -> Integer.parseInt(
                                            ss.getSeat().getSeatNumber().substring(1)
                                    )
                            ))
                            .map(ss -> Map.<String, Object>of(
                                    "number", Integer.parseInt(
                                            ss.getSeat().getSeatNumber().substring(1)
                                    ),
                                    "status", ss.getStatus().name()
                            ))
                            .toList();

                    return Map.<String, Object>of(
                            "row", row.toString(),
                            "seats", seatList
                    );
                })
                .toList();
    }
}