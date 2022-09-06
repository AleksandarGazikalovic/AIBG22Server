package aibg.serverv2.service;

import aibg.serverv2.domain.Player;

public interface LogicService {
    //Šalje zahtev logici da pošalje početni gameState.
    String initializeGame();

    //Dohvata view za konkretnog igrača u momentu slanja zahteva.
    String getPlayerView(int playerIdx, String gameState);

    //Izvršava potez igrača
    String doAction(String action, String gameState);

    //Izbacuje direktno igrača iz gameState-a. Koristi se za izbacivanje kod timeout-a.
    String removePlayer(int playerIdx, String gameState);
}
