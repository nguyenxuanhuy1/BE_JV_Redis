package com.nxh.redis.dto.trip;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripResponse {
    private Long id;
    private String title;
    private String origin;
    private String destination;
    private Double price;
    private LocalDateTime createdAt;
}
