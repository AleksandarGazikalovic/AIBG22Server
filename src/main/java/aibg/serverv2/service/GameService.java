package aibg.serverv2.service;

import aibg.serverv2.dto.*;

public interface GameService {
    //Kreira novu igru na serveru
    DTO createGame(CreateGameRequestDTO dto);

    //Kači odredjenog igrača na odredjenu sesiju.
    DTO joinGame(String token);

    //Šalje zahtev logici da izvrši akciju nad gameState-om.
    DTO doAction(DoActionRequestDTO dto, String token);

    DTO train(TrainPlayerRequestDTO dto, String token);

    DTO doActionTrain(DoActionTrainRequestDTO dto, String token);

    DTO watchGame(int gameId);

    DTO endGame(int gameID, boolean training);
}
