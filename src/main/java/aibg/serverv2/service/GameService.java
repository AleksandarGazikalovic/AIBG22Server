package aibg.serverv2.service;

import aibg.serverv2.dto.*;

public interface GameService {
    //Kreira novu igru na serveru
    DTO createGame(CreateGameReqeustDTO dto);

    //Kači odredjenog igrača na odredjenu sesiju.
    DTO joinGame(JoinGameRequestDTO dto, String token);

    //Šalje zahtev logici da izvrši akciju nad gameState-om.
    DTO doAction(DoActionRequestDTO dto, String token);
}
