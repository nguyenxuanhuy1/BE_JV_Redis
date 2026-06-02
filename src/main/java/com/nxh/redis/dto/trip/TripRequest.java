package com.nxh.redis.dto.trip;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripRequest {
    private String title;
    private String origin;
    private String destination;
    private Double price;
}
