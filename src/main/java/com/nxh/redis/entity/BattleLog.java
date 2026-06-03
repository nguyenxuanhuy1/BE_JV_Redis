package com.nxh.redis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "battle_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleLog {

    @Id
    private String id; // UUID string

    private int roundNumber;
    
    @Column(name = "kingdom_id")
    private String kingdomId; // k-1, k-2 etc. Or "system"

    @Column(length = 2048)
    private String message;
    
    private String createdAt; // time string, e.g. "15:30:22"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id")
    private Battle battle;
}
