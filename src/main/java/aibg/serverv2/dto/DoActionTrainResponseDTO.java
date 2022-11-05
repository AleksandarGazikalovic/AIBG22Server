package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoActionTrainResponseDTO extends DTO {
    private String gameState;
    private String players;

    public DoActionTrainResponseDTO(String gameState, String players) {
        this.gameState = gameState;
        this.players = players;
    }
}
