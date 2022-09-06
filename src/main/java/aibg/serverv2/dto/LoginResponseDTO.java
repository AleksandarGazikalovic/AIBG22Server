package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

/*
    Odgovor na login zahtev.
    JSON oblika:
    {
        "token":"tokenValue"
    }
 */
@Getter
@Setter
public class LoginResponseDTO extends DTO{
    private String token;

    public LoginResponseDTO(String token) {
        this.token = token;
    }
}
