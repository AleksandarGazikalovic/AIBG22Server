package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoActionTrainResponseDTO extends DTO {
    private String gameState;

    public DoActionTrainResponseDTO(String gameState) {
        this.gameState = gameState;
    }
}
