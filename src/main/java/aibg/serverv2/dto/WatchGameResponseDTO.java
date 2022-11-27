package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchGameResponseDTO extends DTO {
    String gameState;

    public WatchGameResponseDTO(String gameState) {
        this.gameState = gameState;
    }
}
