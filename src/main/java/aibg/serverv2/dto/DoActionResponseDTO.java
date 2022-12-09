package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

/*
    Odgovor koji se šalje kada je došlo uspešnog pristupa odredjenjoj sesiji.
    Odgovor je JSON oblika:
        {
        "gameState":"gameStateValue"
        }
 */
@Getter
@Setter
public class DoActionResponseDTO extends DTO {
    private String gameState;
    private String message = null;

    public DoActionResponseDTO(String message, String gameState) {
        this.message = message;
        this.gameState = gameState;
    }
}