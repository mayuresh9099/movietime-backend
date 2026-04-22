package com.movie.admin.controller;

import com.movie.admin.controller.service.AdminService;
import com.movie.theatrevendor.model.TheatreDetails;
import com.movie.theatrevendor.repository.TheatreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TheatreRepository theatreRepository;

    private final AdminService adminService;

    @GetMapping("/theatres/pending")
    public List<TheatreDetails> getPending() {
        return adminService.getPendingTheatres();
    }

    @PutMapping("/theatre/{id}/approve")
    public String approve(@PathVariable Long id) {
        return adminService.approveTheatre(id);
    }

    @PutMapping("/theatre/{id}/reject")
    public String reject(@PathVariable Long id) {
        return adminService.rejectTheatre(id);
    }
}