package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrainPlayerResponseDTO extends DTO{

    private String message;

    public TrainPlayerResponseDTO(String message) {
        this.message = message;
    }
}
