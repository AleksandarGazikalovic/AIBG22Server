package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

/*
    Odgovor na zahtev za kreiranje instance Game objekta.
    Odgovor je JSON oblika:
        {
        "message":"messageText"
        }
 */
@Getter
@Setter
public class CreateGameResponseDTO extends DTO{
    private String message;

    public CreateGameResponseDTO(String message) {
        this.message = message;
    }
}
