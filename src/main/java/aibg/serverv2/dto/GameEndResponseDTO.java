package aibg.serverv2.dto;

/*
    Odgovor koji se šalje kada je došlo do greške u izvršavanju metode.
    Odgovor je JSON oblika:
        {
        "message":"messageText"
        }
 */
public class GameEndResponseDTO extends DTO{
    String message;

    public GameEndResponseDTO(String message) {
        this.message = message;
    }
}
