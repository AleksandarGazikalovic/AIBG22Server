package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
import aibg.serverv2.service.LogicService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/*
    Klasa koja obezbedjuje komunikaciju sa logikom.
 */

@Service
@Getter
@Setter
public class LogicServiceImplementation implements LogicService {
    Logger LOG = LoggerFactory.getLogger(LogicServiceImplementation.class);
    String logicAddress = Configuration.logicAddress;

    @Autowired
    public LogicServiceImplementation() {
    }

    //Šalje zahtev logici da vrati početno stanje igre.
    @Override
    public String initializeGame(){
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.logicAddress + "/getStartGameState"))
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch(Exception ex){
            LOG.info("Greška u logici.");
            return null;
        }
    }

    /*
        Šalje zahtev logici da vrati view za igrača.
     */
    @Override
    public String getPlayerView(int playerIdx, String gameState) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.logicAddress + "/getPlayerView"))
                    .POST(HttpRequest.BodyPublishers.ofString( //OVDE JE FORMAT JAKO BITAN
                            "Player:" + playerIdx + "|"        //ZBOG PARSIRANJA U LOGICI
                            + gameState                        //Player:INT|{gameStateJSON}
                    ))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch(Exception ex){
            LOG.info("Greška u logici.");
            return null;
        }
    }

    /*
        Šalje zahtev logici da izvrši akciju i vrati potez nakon izvršavanja poteza.
     */
    @Override
    public String doAction(String action, String gameState) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.logicAddress + "/doAction"))
                    .POST(HttpRequest.BodyPublishers.ofString( //OVDE JE FORMAT JAKO BITAN
                            "Action:" + action + "|"           //ZBOG PARSIRANJA U LOGICI
                            + gameState                        //Action:{actionJSON}|{gameStateJSON}
                    ))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch(Exception ex){
            LOG.info("Greška u logici.");
            return null;
        }
    }

    @Override
    public String removePlayer(int playerIdx, String gameState) {
        try{
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(this.logicAddress + "/removePlayer"))
                    .POST(HttpRequest.BodyPublishers.ofString( //OVDE JE FORMAT JAKO BITAN
                            "Player:" + playerIdx + "|"        //ZBOG PARSIRANJA U LOGICI
                            + gameState                        //Player:INT|{GameStateJSON}
                    ))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }catch(Exception ex){
            LOG.info("Greška u logici.");
            return null;
        }
    }


}
