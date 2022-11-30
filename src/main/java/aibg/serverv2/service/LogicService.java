package aibg.serverv2.service;

import aibg.serverv2.dto.CreateGameRequestDTO;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface LogicService {
    //Šalje zahtev logici da pošalje početni gameState.
    String initializeGame(CreateGameRequestDTO dto);

    String initializeTrainGame(String mapName, Integer gameId, Integer playerIdx, String username);

    //TODO: Ovo ce se potencijalno skroz izbacivati jer cemo kao
    //      PlayerView vracati celu mapu
    //Dohvata view za konkretnog igrača u momentu slanja zahteva.
    /*String getPlayerView(int playerIdx, String gameState);*/

    //Izvršava potez igrača
    ObjectNode doAction(int currGameIdx, String action, int gameId);

    //Izbacuje direktno igrača iz gameState-a. Koristi se za izbacivanje kod timeout-a.
    String removePlayer(int playerIdx, String gameState);

    ObjectNode trainAction(Integer gameId, String action);

}
