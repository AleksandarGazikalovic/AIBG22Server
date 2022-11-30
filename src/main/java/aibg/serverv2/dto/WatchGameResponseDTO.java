package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchGameResponseDTO extends DTO {
    String gameState;
    long time;
    String playerAttack;

    public WatchGameResponseDTO(String gameState, long time, String playerAttack) {
        this.gameState = gameState;
        this.time = time;
        this.playerAttack = playerAttack;
    }
}
