package com.movie.theatrevendor.model;


import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class TheatreDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String city;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;
    @Enumerated(EnumType.STRING)
    private TheatreStatus status;
    private String address;
    private String state;
    private String pincode;

    @Enumerated(EnumType.STRING)
    private TheatreType type;

    private Integer totalScreens;

    private String facilities;

    private String contactNumber;

    private String description;
}