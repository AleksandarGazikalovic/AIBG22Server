package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
import aibg.serverv2.dto.CreateGameRequestDTO;
import aibg.serverv2.service.LogicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private String requestBody;

    @Autowired
    public LogicServiceImplementation() {
    }

    //Šalje zahtev logici da vrati početno stanje igre.
    @Override
    public String initializeGame(CreateGameRequestDTO dto) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dto);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/getStartGameState"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return getNewState(request);
        } catch (Exception ex) {
            LOG.info("Greška u logici.");
            return null;
        }
    }

    @Override
    public String initializeTrainGame(String mapName, Integer gameId, Integer playerIdx, String username) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("mapName", mapName);
            object.put("gameId", gameId);
            object.put("playerIdx", playerIdx);
            object.put("username", username);
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/getStartTrainGameState"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return getNewState(request);
        } catch (Exception ex) {
            LOG.info("Greška u logici, neuspela inicijalizacija trening igre.");
            return null;
        }
    }

    /*
        Šalje zahtev logici da vrati view za igrača.
     */

    /*
    @Override
    public String getPlayerView(int playerIdx, String gameState) {

        try{
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("playerIdx", playerIdx);
            object.put("gameState", gameState);
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/getPlayerView"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return getNewState(request);
        }catch(Exception ex){
            LOG.info("Greška u logici.");
            return null;
        }
    }*/

    /*
        Šalje zahtev logici da izvrši akciju i vrati potez nakon izvršavanja poteza.
     */
    @Override
    public ObjectNode doAction(int currGameIdx, String action, int gameId) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("playerIdx", currGameIdx);
            object.put("action", action);
            object.put("gameId", gameId);
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/doAction"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectNode node = new ObjectMapper().readValue(response.body(), ObjectNode.class);
            return node;
        } catch (Exception ex) {
            LOG.info("Greška u logici.");
            return null;
        }
    }

    @Override
    public String removePlayer(int playerIdx, String gameState) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("playerIdx", playerIdx);
            object.put("gameState", gameState);
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/removePlayer"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            return getNewState(request);
        } catch (Exception ex) {
            LOG.info("Greška u logici.");
            return null;
        }
    }

    @Override
    public ObjectNode trainAction(Integer gameId, String action) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("gameId", gameId);
            object.put("action", action);
            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/trainAction"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectNode node = new ObjectMapper().readValue(response.body(), ObjectNode.class);
            return node;
        } catch (Exception ex) {
            LOG.info("Greška u logici 1.");
            return null;
        }
    }

    /*Dohvata odgovarajući novi State za svaku metodu i vraća State ako nema problema,
     a ako ima ispisuje odgovarajuću poruku
     */
    private String getNewState(HttpRequest request) throws java.io.IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectNode node = new ObjectMapper().readValue(response.body(), ObjectNode.class);
        if (node.get("gameState") != null) {
            return node.get("gameState").asText();
        }
        return node.get("message").asText();
    }

    /**Salje logici zahtev da zavrsi game i obrise gameID
     */
    @Override
    public boolean removeGame(int GameID, boolean training) { //trenutno se ne koristi return vrednost, moze void
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode object = mapper.createObjectNode();
            object.put("gameID", GameID);
            if(training==true)
                object.put("gameType","Training");
            else object.put("gameType","Normal");

            String requestBody = mapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(Configuration.logicAddress + "/removeGame"))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectNode node = new ObjectMapper().readValue(response.body(), ObjectNode.class);
            return node.get("success").asBoolean();
        } catch (Exception ex) {
            LOG.info("Greška u logici, zahtevano je brisanje Game-a.");
            return false;
        }
    }


}
