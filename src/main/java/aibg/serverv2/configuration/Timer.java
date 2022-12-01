package aibg.serverv2.configuration;

import aibg.serverv2.domain.Game;
import aibg.serverv2.service.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.TimerTask;

public class Timer extends java.util.Timer {
    Game game;
    int second = 1000;
    WebSocketService socketService;

    @Autowired
    public Timer(Game game, WebSocketService socketService) {
        this.game = game;
        this.socketService = socketService;
    }

    public TimerTask task = new TimerTask() {
        @Override
        public void run() {
            game.setTime(game.getTime() - second);
            try {
                socketService.notifySubscribed(game);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (game.getTime() == 0) {
                task.cancel();
            }
        }
    };

}
