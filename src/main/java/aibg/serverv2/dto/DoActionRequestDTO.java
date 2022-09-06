package aibg.serverv2.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;

/*
    Oblik zahteva za izvrsavanje akcije.
    Telo HTTP zahteva treba da bude JSON oblika:
        {
        "action":"action"
        }
 */
@Getter
@Setter
public class DoActionRequestDTO extends DTO{
    @NotEmpty
    private String action;
}
