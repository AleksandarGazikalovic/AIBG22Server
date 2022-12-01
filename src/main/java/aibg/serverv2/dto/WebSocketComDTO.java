package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebSocketComDTO {
    private String gameState;
    private long time;
    private String playerAttack;

    public WebSocketComDTO(String gameState, long time, String playerAttack) {
        this.gameState = gameState;
        this.time = time;
        this.playerAttack = playerAttack;
    }
}
