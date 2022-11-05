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
public class JoinGameResponseDTO extends DTO {
    private int playerIdx;
    private String gameState;

    public JoinGameResponseDTO(int playerIdx, String gameState) {
        this.playerIdx = playerIdx;
        this.gameState = gameState;
    }
}
