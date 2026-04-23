package com.movie.theatrevendor.repository;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class Test {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        //String hash1 = encoder.encode("Publicis@123");
        String hash1 = encoder.encode("password123");
//$2a$10$H8OQXa.HW7eenpn0rXuQ1uO6W831SV3t7o4e8Ep7KYqFsvVV91LXu
        System.out.println(hash1);

        System.out.println(encoder.matches("password123", hash1)); // true
    }
}
