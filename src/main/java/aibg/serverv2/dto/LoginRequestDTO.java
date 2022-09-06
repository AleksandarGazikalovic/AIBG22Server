package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/*
    Oblik zahteva za login.
    Telo treba da bude JSON oblika:
    {
    "username":"usernameValue",
    "password":"passwordValue"
    }
 */
@Getter
@Setter
public class LoginRequestDTO extends DTO{
    @NotNull
    private String username;
    @NotNull
    private String password;
}
