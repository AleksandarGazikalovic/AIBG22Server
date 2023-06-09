package aibg.serverv2.configuration;

import aibg.serverv2.socket.SocketHandler;
import aibg.serverv2.socket.WebSocketHandShakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    private SocketHandler socketHandler;

    @Autowired
    public WebSocketConfiguration(SocketHandler socketHandler) {
        this.socketHandler = socketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(socketHandler, "/streaming")
                .addInterceptors(new WebSocketHandShakeInterceptor()).setAllowedOrigins("*");
    }
}