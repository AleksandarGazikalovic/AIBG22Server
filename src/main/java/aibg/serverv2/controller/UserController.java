package aibg.serverv2.controller;

import aibg.serverv2.dto.DTO;
import aibg.serverv2.dto.LoginRequestDTO;
import aibg.serverv2.dto.LoginResponseDTO;
import aibg.serverv2.service.UserService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.validation.Valid;

@Controller
@RequestMapping("/user")
@Getter
@Setter
public class UserController {
    private UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
        Metoda koja mapira HTTP POST zahtev na URL "http://ip:port/user/login na metodu servisnog sloja
        koja vraća odgovarajući token korisniku.
        Telo zahteva definisano je u @LoginRequestDTO
        U zavisnosti od uspešnosti zahteva vraća:
            @LoginResponseDTO
        tj.
            @ErrorResponseDTO
     */
    @PostMapping("login")
    public ResponseEntity<DTO> login(@RequestBody @Valid LoginRequestDTO dto){
        DTO response = userService.login(dto);

        //Proverava uspesnost akcije
        if(response instanceof LoginResponseDTO){
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        }else{
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
    }

}
