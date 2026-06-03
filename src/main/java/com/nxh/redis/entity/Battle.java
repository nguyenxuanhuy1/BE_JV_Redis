package com.nxh.redis.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "battles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Battle {

    @Id
    private String id;

    private int maxRound;
    
    private int round;

    private String status; // WAITING, RUNNING, FINISHED

    @Builder.Default
    @OneToMany(mappedBy = "battle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Kingdom> kingdoms = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "battle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Tile> tiles = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "battle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BattleLog> logs = new ArrayList<>();

    private LocalDateTime createdAt;
}
