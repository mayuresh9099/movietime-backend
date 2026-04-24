package com.movie.theatrevendor.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "screens", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"theatre_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🎭 Each screen belongs to one theatre
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "theatre_id", nullable = false)
    private TheatreDetails theatre;

    // Example: Screen 1, Screen 2, IMAX, Audi 1
    @Column(nullable = false)
    private String name;

    // Total seats in this screen
    @Column(nullable = false)
    private Integer totalSeats;

    // Optional: screen type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScreenType type = ScreenType.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ScreenStatus status = ScreenStatus.ACTIVE;

    // 🔁 One screen → many shows
    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Show> shows;
}