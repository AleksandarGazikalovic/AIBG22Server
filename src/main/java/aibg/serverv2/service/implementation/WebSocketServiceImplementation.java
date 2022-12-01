package aibg.serverv2.service.implementation;

import aibg.serverv2.domain.Game;
import aibg.serverv2.dto.WebSocketComDTO;
import aibg.serverv2.service.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Service
@Getter
@Setter
public class WebSocketServiceImplementation implements WebSocketService {
    Logger LOG = LoggerFactory.getLogger(WebSocketServiceImplementation.class);
    private Hashtable<Integer, List<WebSocketSession>> subscriptions = new Hashtable<>();

    @Override
    public boolean subscribe(WebSocketSession session) {
        //Dohvata gameId date sesije
        Integer gameId = (Integer) session.getAttributes().get(WebSocketService.GAME_ID);

        if (gameId == null) {
            LOG.info("GameID nije postavljen uspešno za vreme povezivanja na WebSocket.");
            return false;
        }

        //Proverava da li postoji lista subscribera za dati gameId, i kreira novu listu ako ne postoji.
        List<WebSocketSession> sessions = subscriptions.computeIfAbsent(gameId, k -> new ArrayList<>());

        return sessions.add(session);
    }

    @Override
    public boolean unsubscribe(WebSocketSession session) {
        //Dohvata gameId date sesije
        Integer gameId = (Integer) session.getAttributes().get(WebSocketService.GAME_ID);

        if (gameId == null) {
            LOG.info("GameID nije uspešno pronadjen za vreme skidanja sa WebSocket-a.");
            return false;
        }

        List<WebSocketSession> sessions = subscriptions.get(gameId);

        if (sessions == null) {
            LOG.info("Nema WebSocket-a za dati id.");
            return false;
        }

        return sessions.remove(session);
    }

    @Override
    public void notifySubscribed(Game game) throws IOException {
        //Dohvata sve websockete povezane za dati gameId
        List<WebSocketSession> sessions = subscriptions.get(game.getGameId());
        ObjectMapper mapper = new ObjectMapper();

        if (sessions == null) {
            LOG.info("Ne postoji nijedan WebSocket povezan na ovu igru.");
            return;
        }

        synchronized (this) {
            String message = mapper.writeValueAsString(new WebSocketComDTO(game.getGameState(), game.getTime(), game.getPlayerAttack()));
            for (WebSocketSession ses : sessions) {
                if (ses.isOpen()) {
                    if (ses.getAttributes().get("password").toString().equalsIgnoreCase("salamala"))
                        ses.sendMessage(new TextMessage(message));
                    else
                        ses.sendMessage(new TextMessage("{\"message\" : \"Wrong password\"}"));
                }
            }
        }
    }
}
