package com.nxh.redis.dto.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KingdomDto {
    private String id;
    private String name;
    private String model;
    private int population;
    private int gold;
    private int oil;
    private int supplies;
    private int energy;
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
    private List<Integer> scoreHistory;
    private boolean alive;
    private boolean ready;
    private String color;
}
