package aibg.serverv2.service;

public interface LogicService {
    //Šalje zahtev logici da pošalje početni gameState.
    String initializeGame(String mapName);

    String initializeTrainGame(String mapName, Integer gameId, Integer playerIdx);

    //TODO: Ovo ce se potencijalno skroz izbacivati jer cemo kao
    //      PlayerView vracati celu mapu
    //Dohvata view za konkretnog igrača u momentu slanja zahteva.
    /*String getPlayerView(int playerIdx, String gameState);*/

    //Izvršava potez igrača
    String doAction(String action, String gameState);

    //Izbacuje direktno igrača iz gameState-a. Koristi se za izbacivanje kod timeout-a.
    String removePlayer(int playerIdx, String gameState);

    String trainAction(Integer gameId, String action);
}
