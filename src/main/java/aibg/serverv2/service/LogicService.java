package aibg.serverv2.service;

public interface LogicService {
    //Šalje zahtev logici da pošalje početni gameState.
    String initializeGame(String mapName);

    //Dohvata view za konkretnog igrača u momentu slanja zahteva.
    String getPlayerView(int playerIdx, String gameState);

    //Izvršava potez igrača
    String doAction(String action, String gameState);

    //Izbacuje direktno igrača iz gameState-a. Koristi se za izbacivanje kod timeout-a.
    String removePlayer(int playerIdx, String gameState);
}
