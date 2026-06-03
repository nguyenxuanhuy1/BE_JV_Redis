package com.nxh.redis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dbId;

    private String id; // tile-x-y
    private String code; // A1, J10 etc.
    private int x;
    private int y;
    
    private String type; // PLAIN, GOLD_MINE, FOREST, FARM, MOUNTAIN, CAPITAL
    
    @Column(name = "owner_kingdom_id")
    private String ownerKingdomId; // k-1, k-2 etc. Or null
    
    private int level;
    private int defenseBonus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id")
    private Battle battle;
}
