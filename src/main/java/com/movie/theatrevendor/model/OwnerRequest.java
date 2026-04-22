package com.movie.theatrevendor.model;

import lombok.Data;

@Data
public class OwnerRequest {
    private Long userId;
    private String businessName;
    private Boolean isVerified;
}