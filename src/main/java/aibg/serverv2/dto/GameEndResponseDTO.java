package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

/*
    Odgovor koji se šalje kada je došlo do greške u izvršavanju metode.
    Odgovor je JSON oblika:
        {
        "message":"messageText"
        }
 */
@Getter
@Setter
public class GameEndResponseDTO extends DTO{
    String message;

    public GameEndResponseDTO(String message) {
        this.message = message;
    }
}
