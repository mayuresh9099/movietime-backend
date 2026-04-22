package com.movie.theatrevendor.model;


import com.movie.module.user.entities.User;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Link to User (Auth layer)
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // 🏢 Business Info
    private String businessName;

    private Boolean isVerified = false;
}