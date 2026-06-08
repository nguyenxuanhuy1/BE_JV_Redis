package com.nxh.redis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip extends BaseEntity {

    private String title;        // Tên chuyến đi
    private String origin;       // Điểm đi
    private String destination;  // Điểm đến
    private Double price;        // Giá vé
}