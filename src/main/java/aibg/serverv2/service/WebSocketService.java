package aibg.serverv2.service;

import aibg.serverv2.domain.Game;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public interface WebSocketService {
    String GAME_ID = "SocketService_GAME_ID";

    //Povezuje konkretnu websocket sesiju na odgovarajuću igru.
    boolean subscribe(WebSocketSession session);

    //Skida websocket sa igre.
    boolean unsubscribe(WebSocketSession session);

    void notifySubscribed(Game game) throws IOException;
}
