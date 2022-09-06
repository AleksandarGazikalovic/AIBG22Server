package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/*
    Oblik zahteva za kreiranje instance Game objekta.
    Telo HTTP zahteva treba da bude JSON oblika:
        {
        "gameId":INT,
        "playerUsernames": ["player1","player2",...,"playerN"]
        }
 */
@Getter
@Setter
public class CreateGameReqeustDTO extends DTO{
    @NotNull
    private int gameId;
    @NotEmpty
    private List<String> playerUsernames;
}
