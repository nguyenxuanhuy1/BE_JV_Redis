package com.nxh.redis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kingdoms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Kingdom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dbId;

    private String id; // k-1, k-2 etc.
    
    private String name;
    private String model;
    
    @Column(name = "api_key")
    private String apiKey;
    
    private String color;

    private int population;
    private int gold;
    private int oil;
    private int supplies;
    private int energy;

    // Military units
    private int infantry;
    private int tanks;
    private int aircraft;
    private int artillery;
    private int navy;
    private int drones;
    
    private int soldiers;
    private int tech;
    private int morale;
    private int score;

    @Column(length = 2048)
    private String scoreHistory; // comma-separated scores, e.g. "100,105"

    private boolean alive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id")
    private Battle battle;
}
