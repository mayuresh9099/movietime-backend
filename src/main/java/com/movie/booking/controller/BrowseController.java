package com.movie.booking.controller;

import com.movie.booking.service.BrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/browse")
@RequiredArgsConstructor
public class BrowseController {

    private final BrowseService browseService;

    /**
     * Browse theatres showing a specific movie in a city
     * GET /api/browse/theatres?movie=Avengers&city=Mumbai
     */
    @GetMapping("/theatres")
    public ResponseEntity<?> browseTheatres(
            @RequestParam String movie,
            @RequestParam String city) {

        try {
            List<Map<String, Object>> theatres = browseService.browseTheatresByMovieAndCity(movie, city);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "movie", movie,
                    "city", city,
                    "totalTheatres", theatres.size(),
                    "theatres", theatres
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get show timings for a specific theatre and date
     * GET /api/browse/shows/{theatreId}?date=2026-04-25
     */
    @GetMapping("/shows/{theatreId}")
    public ResponseEntity<?> getShowTimings(
            @PathVariable Long theatreId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        try {
            List<Map<String, Object>> shows = browseService.getShowTimingsForDate(theatreId, date);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "theatreId", theatreId,
                    "date", date,
                    "totalShows", shows.size(),
                    "shows", shows
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
