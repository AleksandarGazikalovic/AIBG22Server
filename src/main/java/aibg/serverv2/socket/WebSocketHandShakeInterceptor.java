package aibg.serverv2.socket;

import aibg.serverv2.service.WebSocketService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Component
@Getter
@Setter
public class WebSocketHandShakeInterceptor implements HandshakeInterceptor {

    Logger LOG = LoggerFactory.getLogger(WebSocketHandShakeInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        HttpServletRequest req = ((ServletServerHttpRequest) request).getServletRequest();

        String gameId = req.getParameter("gameId");
        String password = req.getParameter("password");

        if(gameId == null){
            LOG.info("GameId is null during handshake.");
            return false;
        }

        Integer gameID = Integer.parseInt(gameId);
        attributes.put(WebSocketService.GAME_ID, gameID);
        attributes.put("password", password);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
