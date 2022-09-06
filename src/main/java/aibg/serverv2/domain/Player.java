package aibg.serverv2.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Player extends User{
    //ID igre u kojoj se ovaj igrač trenutno nalazi.
    private int currGameId;
    //Koji indeks ima igrač u igri u kojoj se trenutno nalazi.
    private int currGameIdx;


    public Player(String username, String password) {
        super(username, password);
    }
}
