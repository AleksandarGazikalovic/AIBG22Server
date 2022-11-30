package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchGameResponseDTO extends DTO {
    String gameState;
    long time;

    public WatchGameResponseDTO(String gameState, long time) {
        this.gameState = gameState;
        this.time = time;
    }
}
