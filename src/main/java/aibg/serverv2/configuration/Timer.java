package aibg.serverv2.configuration;

import aibg.serverv2.domain.Game;

import java.util.TimerTask;

public class Timer extends java.util.Timer {
    Game game;
    int second = 1000;

    public Timer(Game game) {
        this.game = game;
    }

    public TimerTask task = new TimerTask() {
        @Override
        public void run() {
            game.setTime(game.getTime() - second);
            if (game.getTime() == 0) {
                task.cancel();
            }
        }
    };

}
