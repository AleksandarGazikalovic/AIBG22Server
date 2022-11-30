package aibg.serverv2.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
    Predstavlja trenutno aktivnu sesiju na serveru.
 */
@Getter
@Setter
public class Game {
    private int gameId;
    private List<Player> players;
    private Player currPlayer;
    private String gameState;
    private long time;
    private int playersJoined;
    private String playerAttack;
    private boolean gameStarted = false;

    @JsonIgnore
    private Boolean activeDoActionTrainCall;

    @JsonIgnore
    private LocalDateTime timeOfBeginning; // trenutak kada je igra kreirana
    @JsonIgnore
    private long minutes; // koliko vremena treba igra da traje, pocev od prvog poteza
    @JsonIgnore
    private Boolean firstTurn;


    public Game(int gameId) {
        this.gameId = gameId;
        this.players = new ArrayList<>();
        this.playersJoined = 0;
        this.activeDoActionTrainCall = false;
        this.firstTurn = true;
    }

    //Pomera na sledećeg igrača.
    public void next() {
        int idx = players.indexOf(currPlayer);
        this.currPlayer = players.get((idx + 1) % (players.size()));
    }

    //Izbacuje user-a iz rotacije, ako je trenutno na potezu, pomera na sledećeg.
    public void remove(Player player) {
        if (player.equals(currPlayer)) {
            next();
        }
        players.remove(player);
    }
}
