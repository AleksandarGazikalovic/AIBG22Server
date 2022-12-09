package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteGameResponseDTO extends DTO {
    private String message;

    public DeleteGameResponseDTO(String message) {
        this.message = message;
    }
}
