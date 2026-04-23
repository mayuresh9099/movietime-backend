package com.movie.theatrevendor.model;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "theatre_details",
        indexes = {
                @Index(name = "idx_city_status", columnList = "city, status")
        }
)
@Data
public class TheatreDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TheatreStatus status;

    @Column(nullable = false)
    private String address;

    private String state;

    private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TheatreType type;

    @Column(nullable = false)
    private Integer totalScreens;

    // Keep simple for now (later normalize if needed)
    private String facilities;

    private String contactNumber;

    private String description;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}