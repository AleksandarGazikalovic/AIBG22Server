package aibg.serverv2.domain;

import lombok.Getter;
import lombok.Setter;

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
    private int playersJoined;

    public Game(int gameId) {
        this.gameId = gameId;
        this.players = new ArrayList<>();
        this.playersJoined = 0;
    }

    //Pomera na sledećeg igrača.
    public void next(){
        int idx = players.indexOf(currPlayer);
        this.currPlayer = players.get((idx+1)%(players.size()));
    }

    //Izbacuje user-a iz rotacije, ako je trenutno na potezu, pomera na sledećeg.
    public void remove(Player player){
        if(player.equals(currPlayer)){
            next();
        }
        players.remove(player);
    }
}
