package aibg.serverv2.controller;

import aibg.serverv2.dto.*;
import aibg.serverv2.security.CheckSecurity;
import aibg.serverv2.service.GameService;
import aibg.serverv2.service.LogicService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Controller
@RequestMapping("/game")
@Getter
@Setter
public class GameController {
    private GameService gameService;
    private LogicService logicService;

    @Autowired
    public GameController(GameService gameService, LogicService logicService) {
        this.gameService = gameService;
        this.logicService = logicService;
    }

    /*
        Metoda koja mapira HTTP POST zahtev na URL: http://ip:port/game/createGame u metodu
        servisnog sloja za kreaciju instance game-a.
        Telo zahteva treba da bude JSON definisan u @CreateGameRequestDTO
        Zahtev može da pošalje samo već ulogovan korisnik koji je Admin.
        U zavisnosti od toga da li je zahtev uspešno izvršen, odgovor je definisan u
            @CreateGameResponseDTO
        tj.
            @ErrorResponseDTO
     */
    @PostMapping("/createGame")
    @CheckSecurity(roles = {"A"})
    public ResponseEntity<DTO> createGame(@RequestBody @Valid CreateGameRequestDTO dto,
                                          @RequestHeader("Authorization") String authorization) {
        DTO response = gameService.createGame(dto);

        if (response instanceof CreateGameResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    /*
       Metoda koja mapira HTTP POST zahtev na URL: http://ip:port/user/joinGame u metodu
       servisnog sloja koja dodaje odredjenog igrača na odredjenu sesiju.
       Telo zahteva treba da bude JSON definisan u @JoinGameRequestDTO
       Zahtev može da pošalje samo već ulogovan korisnik koji je Player.
       U zavisnosti od toga da li je zahtev uspešno izvršen, odgovor je definisan u
            @JoinGameResponseDTO
        tj.
            @ErrorResponseDTO
     */
    @GetMapping("/joinGame")
    @CheckSecurity(roles = {"P"})
    public ResponseEntity<DTO> joinGame(@RequestHeader("Authorization") String authorization) {
        DTO response = gameService.joinGame(authorization);

        if (response instanceof JoinGameResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    /*
        Metoda koja mapira HTTP POST zahtev na URL: http://ip:port/game/doAction u metodu
        servisnog sloja koja izvršava potez igrača nad gameState-om.
        Telo zahteva definisano je u @DoActionRequestDTO
        Zahtev može da pošalje samo već ulogovan korisnik koji je Player.
       U zavisnosti od toga da li je zahtev uspešno izvršen, odgovor je definisan u
            @DoActionResponseDTO
        tj.
            @ErrorResponseDTO
     */
    @PostMapping("/doAction")
    @CheckSecurity(roles = {"P"})
    public ResponseEntity<DTO> doAction(@RequestBody @Valid DoActionRequestDTO dto,
                                        @RequestHeader("Authorization") String authorization) {
        DTO response = gameService.doAction(dto, authorization);

        if (response instanceof DoActionResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/train")
    public ResponseEntity<DTO> train(@RequestBody TrainPlayerRequestDTO dto,
                                     @RequestHeader("Authorization") String authorization) {
        DTO response = gameService.train(dto, authorization);

        if (response instanceof TrainPlayerResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/actionTrain")
    public ResponseEntity<DTO> actionTrain(@RequestBody DoActionTrainRequestDTO dto,
                                           @RequestHeader("Authorization") String authorization) {
        DTO response = gameService.doActionTrain(dto, authorization);

        if (response instanceof DoActionTrainResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping()
    @CrossOrigin(origins = "http://localhost:1234/")
    public ResponseEntity<DTO> watchGame(@RequestParam("gameId") Integer gameId, @RequestParam("password") String password) {
        if (password.equalsIgnoreCase("salamala")) {
            DTO response = gameService.watchGame(gameId);

            if (response instanceof WatchGameResponseDTO) {
                return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
            } else {
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        } else {
            return null;
        }
    }

    @PostMapping("/deleteGame")
    @CheckSecurity(roles = {"A"})
    public ResponseEntity<DTO> createGame(@RequestBody @Valid DeleteGameRequestDTO dto,
                                          @RequestHeader("Authorization") String authorization) {
        DTO response = gameService.endGame(dto.getGameId(), dto.isTraining());

        if (response instanceof CreateGameResponseDTO) {
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
    }
}
