package aibg.serverv2.socket;

import aibg.serverv2.service.WebSocketService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
@Getter
@Setter
public class SocketHandler extends AbstractWebSocketHandler {

    private WebSocketService webSocketService;

    @Autowired
    public SocketHandler(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        webSocketService.subscribe(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
        webSocketService.unsubscribe(session);
    }
}