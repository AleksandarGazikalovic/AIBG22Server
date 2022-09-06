package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/*
    Oblik tela HTTP zahteva za pristup odredjenoj sesiji.
    Treba da bude JSON oblika:
    {
        "gameId":"INT"
    }
 */
@Getter
@Setter
public class JoinGameRequestDTO extends DTO{
    @NotNull
    private int gameId;
}
