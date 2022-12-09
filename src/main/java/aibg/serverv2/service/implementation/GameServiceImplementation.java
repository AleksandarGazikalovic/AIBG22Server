package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
import aibg.serverv2.configuration.Timer;
import aibg.serverv2.domain.Game;
import aibg.serverv2.domain.Player;
import aibg.serverv2.domain.User;
import aibg.serverv2.dto.*;
import aibg.serverv2.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Claims;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;


@Service
@Getter
@Setter
public class GameServiceImplementation implements GameService {
    //Not autowired -- ne ide u konstruktor
    private Logger LOG = LoggerFactory.getLogger(GameService.class);
    private Map<Integer, Game> games = new TreeMap<>();
    private PriorityQueue<Integer> reuseKeys = new PriorityQueue<>();
    private Map<Integer, Game> gamesTraining = new TreeMap<>();
    private PriorityQueue<Integer> reuseKeysTraining = new PriorityQueue<>();
    private int highestUnusedKey = 0;
    private int highestUnusedKeyTraining = 0;
    private int firstMove = 0;

    private ObjectMapper mapper = new ObjectMapper();
    private static final long GAME_JOIN_TIMEOUT = 100000;
    private static final long MOVE_TIMEOUT = 1500; //bilo 500
    private static final long UPDATE_TIMEOUT = 3000; //bilo 1500
    private static final long PLAYER_SLEEP_TIME = 200;
    //Autowired -- ide u konstruktor
    private LogicService logicService;
    private TokenService tokenService;
    private UserService userService;
    private WebSocketService socketService;

    @Autowired
    public GameServiceImplementation(LogicService logicService, TokenService tokenService, UserService userService, WebSocketService socketService) {
        this.logicService = logicService;
        this.tokenService = tokenService;
        this.userService = userService;
        this.socketService = socketService;
    }

    /*
            Kreira novu instancu Game objekta za parametre zadate u @CreateGameDTO
            i dodaje ga na listu aktivnih sesija.
         */
    @Override
    public DTO createGame(CreateGameRequestDTO dto) {

        // provera da li je neki od usera vec u nekom gejmu; ako jeste, ne dozvoli da se pravi gejm
        for (String username : dto.getPlayerUsernames()) {
            for (Game g : games.values()) {
                for (Player p : g.getPlayers()) {
                    if (p.getUsername().equals(username)) {
                        return new ErrorResponseDTO("Neuspesno kreiranje igre, igrac:" + username + " je vec u igri sa ID:" + g.getGameId());
                    }
                }
            }
        }

        //Dohvata slobodan gameID i setuje ga u dto za slanje
        int gameID = getAvailableKey();
        dto.setGameId(gameID);
        //Proverava da li već postoji igra sa zadatim ID-om.
        if (games.containsKey(gameID)) {
            LOG.info("Igra sa gameId:" + gameID + "već postoji.");
            return new ErrorResponseDTO("Igra sa gameId:" + gameID + "već postoji.");
        }
        //Dohvata početno stanje igre
        String gameState = logicService.initializeGame(dto);
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici");
        }
        Game game = new Game(gameID);
        game.setGameState(gameState);

        //Postavlja vreme trajanja igre i inicijalizuje timer
        game.setTime(dto.getTime() * 60 * 1000);
        game.setTimer(new Timer(game, socketService));

        //Dodaje igrače u igru, postavlja im index-e.
        List<Player> players = userService.addPlayers(dto.getPlayerUsernames(), gameID);
        if (players == null) {
            return new ErrorResponseDTO("Greška pri dodavanju igrača u igru.");
        }
        game.setPlayers(players);
        //Postavlja početnog igrača.
        game.setCurrPlayer(players.get(0));
        try {
            socketService.notifySubscribed(game);
        } catch (IOException e) {
            return new ErrorResponseDTO("Greška pri WebSocket komunikaciji");
        }

        //Uspešno kreiran game, dodaje se u listu postojećih sesija, i vraća se odgovor.
        games.put(gameID, game);
        return new CreateGameResponseDTO("Game sa id:" + gameID + " uspešno napravljen.");
    }

    //Kači odredjenog igrača na odredjenu sesiju.
    @Override
    public DTO joinGame(String token) {
        Claims claims = tokenService.parseToken(token);
        //Proverava da li je token parsiran
        if (token == null) {
            LOG.info("Token nije parsiran kako treba.");
            return new ErrorResponseDTO("Token nije parsiran kako treba.");
        }

        //Dohvata usera koji je poslao zahtev.
        Player player = null;
        for (User u : userService.getUsers()) { // ovo sam u optimizaciji izmenio, useri su u hesmapi
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
            }
        }

        if (player == null) {
            //Ne dešava se zato što je igrač već morao da prodje autorizaciju.
            LOG.info("Igrač ne postoji.");
            return new ErrorResponseDTO("Igrač ne postoji.");
        }

        //Dohvata Game na koji igrač pokušava da se prikači.
        Game game = games.get(player.getCurrGameId());

        //Proverava da li postoji Game sa datim ID-om.
        if (game == null) {
            LOG.info("Pokušano da se prikači na nepostojeću igru.");
            return new ErrorResponseDTO("Igra sa id: " + player.getCurrGameId() + " ne postoji.");
        }

        //Poverava da li se igrač nalazi u datoj igri, i ako se nalazi ubacuje ga.
        if (!game.getPlayers().contains(player)) {
            LOG.info("Igrač pokušao da se priključi igri kojoj ne pripada.");
            return new ErrorResponseDTO("Ne nalazite se u igri kojoj pokušavate da se priključite.");
        }

        //ja bih bez ovog dela; ok igrac se prikljucio, kad dodje red na njega bice mu poslat gameState
        // i tako zna da treba igrati, akjo u medjuvremenu salje zahteve odbacuju se
        if (waitForGame(player.getCurrGameId())) {
            if (!game.isGameStarted()) {
                game.getTimer().schedule(game.getTimer().task, 0, 1000);
                game.setGameStarted(true);
            }
            return new JoinGameResponseDTO(player.getCurrGameIdx(), game.getGameState());
        }

        return new ErrorResponseDTO("Igrač timeout-ovao dok je čekao početak sesije.");
    }

    //Izvršava potez nad sesijom u kojoj je igrač.
    @Override
    public DTO doAction(DoActionRequestDTO dto, String token) {
        Claims claims = tokenService.parseToken(token);
        //Dohvata usera koji je poslao zahtev.
        Player player = null;
        for (User u : userService.getUsers()) {
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
            }
        }

        if (player == null) {
            //Ne dešava se zato što je igrač već morao da prodje autorizaciju.
            LOG.info("Igrač ne postoji.");
            return new ErrorResponseDTO("Igrač ne postoji.");
        }

        //Dohvate igru u kojoj je igrač
        Game game = games.get(player.getCurrGameId());      //ako je igra gotova, pokusace da dohvati ID -1, pa ce biti null
        //Proverava da li je igrač pristupio igri.
        if (game == null) {
            if (player.getCurrGameId() == -1) {
                //LOG.info("Igra je zavrsena!");
                return new GameEndResponseDTO("Igra je završena.");
            } else {
                LOG.info("Igrač nije pristupio igri ili igra ne postoji.");
                return new ErrorResponseDTO("Niste pristupili igri, ili pokšuvate da igrate u igri koja ne postoji.");
            }

        }

        String message = "null";
        synchronized (game) {
            if (player != game.getCurrPlayer()) {
                if (game.getCurrPlayer() == null) {
                    return new GameEndResponseDTO("Igra je završena.");
                } else if (game.getPlayers().size() <= 1) {   // ostao je jedan igrac, a nije pozivan endGame
                    endGame(game.getGameId());
                } else {
                    return new DoActionResponseDTO("Niste na potezu", game.getGameState());
                }
            }


            //Postavlja nov gameState nakon izvšrenog poteza.
            ObjectNode actionNode = logicService.doAction(player.getCurrGameIdx(), dto.getAction(), player.getCurrGameId());
            String gameState;
            String playerAttack;
            if (actionNode != null) {
                if (actionNode.get("message") != null) {
                    message = actionNode.get("message").asText();
                }
                try {
                    gameState = actionNode.get("gameState").asText();
                    if (gameState != null) {
                        game.setGameState(gameState);
                    }
                } catch (Exception e) {
                    LOG.info("Nešto popucalo u logici pa nije vratila gameState");
                }
                playerAttack = actionNode.get("playerAttack").asText();
                game.setPlayerAttack(playerAttack);
            }
            try {
                socketService.notifySubscribed(game);
            } catch (IOException e) {
                return new ErrorResponseDTO("Greška pri WebSocket komunikaciji");
            }

            //Dohvata preostale igrače nakon poteza.
            try {
                JsonNode node = mapper.readValue(game.getGameState(), JsonNode.class);
                //Proverava da li je postoji pobednik. TODO testirati kako se vraća null string.
                //testirati da li je okej, vrv treba asText();
                if (game.getTime() == 0 || game.getPlayers().size() <= 1) {
                    //synchronized (game) { ne mora ima okruzujuci
                    endGame(player.getCurrGameId());

                    return new GameEndResponseDTO("Igra je završena.");
                }

            } catch (Exception ex) {
                return new ErrorResponseDTO("Greška pri parsiranju JSON-a gameState-a.");
            }

            //synchronized (game) ne mora posto vec ima okruzujuci
            //Pomera na sledećeg igrača
            game.next();

        }
        //Čeka da igrač ponovo dodje na red, i tada mu vraća update-ovan gameState.
        if (waitForUpdate(player)) {
            return new DoActionResponseDTO(message, game.getGameState());
        } else {
            if (game.getCurrPlayer() != null) {
                endGame(game.getGameId());
            }
            return new GameEndResponseDTO("Igra je završena.");
        }

    }

    public DTO watchGame(int gameId) {

        Game game = games.get(gameId);
        if (game != null) {
            return new WatchGameResponseDTO(game.getGameState(), game.getTime(), game.getPlayerAttack());
        } else {
            game = gamesTraining.get(gameId);
            if (game != null) {
                return new WatchGameResponseDTO(game.getGameState(), game.getTime(), game.getPlayerAttack());
            } else {
                return new ErrorResponseDTO("Greška pri gledanju partije");
            }
        }
    }

    //suvisno?
    //Metoda koja obezbedjuje time-out sistem za početak partije.
    private boolean waitForGame(int gameId) {
        //Vreme kad igrač počinje da čeka.
        long start = System.currentTimeMillis();

        Game game = games.get(gameId);
        if (game == null) {
            //Ne bi smelo da se desi pošto se proverava null u joinGame metodi.
            return false;
        }
        //Povećava broj igrača koji su u igri.
        game.setPlayersJoined(game.getPlayersJoined() + 1);

        while (game.getPlayersJoined() != Configuration.noOfPlayers) {
            try {
                Thread.sleep(PLAYER_SLEEP_TIME);
            } catch (Exception ex) {
                //Ne bi trebalo da se dešava.
                LOG.info(ex.getMessage());
            }
            if (System.currentTimeMillis() > start + GAME_JOIN_TIMEOUT) {
                //Igrač predugo čeka na potez, dolazi do timeout-a.
                LOG.info("Igrač timeout-ovao dok je čekao početak sesije.");
                return false;
            }
        }

        return true;
    }

    private boolean waitForMyMove(Player p) {
        long start = System.currentTimeMillis();

        //Proverava da li postoji game.
        Game game = games.get(p.getCurrGameId());
        if (game == null) {
            //Ne bi smelo da se desi pošto se proverava null u joinGame metodi.
            return false;
        }

        while (!game.getCurrPlayer().equals(p)) {
            try {
                Thread.sleep(PLAYER_SLEEP_TIME);
            } catch (Exception ex) {
                //Ne bi trebalo da se desi.
                LOG.info(ex.getMessage());
            }
            if (System.currentTimeMillis() > start + MOVE_TIMEOUT) {
                //Igrač predugo čeka na potez, dolazi do timeout-a.
                LOG.info("Igrač timeout-ovao dok je čekao početak sesije.");
                return false;
            }
        }

        return true;
    }

    /**
     * Player is waiting for his turn; if he waits too long, he kicks out player who is currently supposed to play.
     *
     * @Returns False if game happends to end during this method, otherwise True
     */
    private boolean waitForUpdate(Player p) {
        long start = System.currentTimeMillis();

        //Proverava da li postoji game.
        Game game = games.get(p.getCurrGameId());
        if (game == null) {
            return false;
        }
        int noOfPlayers = game.getPlayers().size();

        //proveri jel game postoji
        while (game.getCurrPlayer() != p) {
            try {
                Thread.sleep(PLAYER_SLEEP_TIME);
            } catch (Exception ex) {
                //Ne bi trebalo da se desi.
                LOG.info(ex.getMessage());
            }
            synchronized (game) {
                if (games.get(p.getCurrGameId()) == null || games.get(p.getCurrGameId()) != game || game.getPlayers().size() <= 1) //drugi uslov ipak suvisan?
                    return false;
                if (System.currentTimeMillis() > start + UPDATE_TIMEOUT) {
                    // Proverava da li je neki igrač ispao u medjuvremenu i setuje novi broj igrača za proveru
                    if (noOfPlayers != game.getPlayers().size()) {
                        start = System.currentTimeMillis() + UPDATE_TIMEOUT;
                        noOfPlayers = game.getPlayers().size();
                        continue;
                    } else {
                        //Ako je i dalje u igri, a nije dočekao potez, znači da je trenutni igrač time-out-ovao.
                        //Izbacuje igrača iz gameState-a i serverske Game klase.
                        game.setGameState(logicService.removePlayer(game.getGameId(), game.getCurrPlayer().getCurrGameIdx()));
                        Player kickPlayer = game.getCurrPlayer();
                        game.next();
                        logicService.removePlayer(kickPlayer.getCurrGameId(), kickPlayer.getCurrGameIdx());
                        game.getPlayers().remove(kickPlayer);
                        if (game.getPlayers().size() <= 1)
                            return false; //ako ostane jedan igrac igra je zavrsena
                    }
                    //Igrač predugo čeka na potez, dolazi do timeout-a.
                    LOG.info("Igrač " + p.getCurrGameIdx() + " timeout-ovao");
                    //return false;
                }
            }
        }
        return true;
    }


    // Inicijalizuje igru za trening i stavlja je u games
    @Override
    public DTO train(TrainPlayerRequestDTO dto, String token) {

        Claims claims = tokenService.parseToken(token);
        //Dohvata usera koji je poslao zahtev.
        Player player = null;

        for (User u : userService.getUsers()) {
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
                // provera da li je neki od usera vec u nekom gejmu; ako jeste, ne dozvoli da se pravi gejm
                for (Game g : gamesTraining.values()) {
                    for (Player p : g.getPlayers()) {
                        if (p.getUsername().equals(player.getUsername())) {
                            return new ErrorResponseDTO("Neuspesno kreiranje igre, igrac:" + player.getUsername() + " je vec u igri sa ID:" + g.getGameId());
                        }
                    }
                }

            }
        }

        if (player == null) { // ako je igrac poslao los token
            LOG.info("Greska u train metodi, igrač ne postoji.");
            return new ErrorResponseDTO("Greska u train metodi, igrač ne postoji.");
        }

        //proverava poslato vreme trajanja partije
        if (dto.getTime() > 10) {
            LOG.info("Pokušano pravljenje igrice duže od 10minuta");
            return new ErrorResponseDTO("Pokušali ste da napravite igru dužu od 10 minuta, što nije dozvoljeno.");
        }

        int currTrainGameId = getAvailableKeyTrain();

        String gameState = logicService.initializeTrainGame("FinalMapTopicAIBGaziBre.txt", currTrainGameId, dto.getPlayerIdx(), player.getUsername());
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici usled kreiranja train igre");
        }
        Game game = new Game(currTrainGameId);

        game.setGameState(gameState);

        //postavlja vreme i pravi novi tajmer
        game.setTime(dto.getTime() * 60 * 1000);
        game.setTimer(new Timer(game, socketService));


        //Dodaje igrača u igru, postavlja mu index.
        player.setCurrGameId(game.getGameId());
        player.setCurrGameIdx(dto.getPlayerIdx());
        game.getPlayers().add(player);
        try {
            socketService.notifySubscribed(game);
        } catch (IOException e) {
            return new ErrorResponseDTO("Greška pri WebSocket komunikaciji");
        }

        //Uspešno kreiran game, dodaje se u listu postojećih sesija i vraca poruku o uspesno napravljenom game-u
        gamesTraining.put(currTrainGameId, game);
        return new TrainPlayerResponseDTO("TrainingGame sa id-ijem: " + game.getGameId() + "uspešno napravljen.");

    } //TODO
    // ZA SAD NE SALJEM SVE AKCIJE BOTA

    // Obavlja jednu rundu igre u trening rezimu, igrac posalje potez koji zeli da odigra
    // uporedo sa jos 3 bota, svi odigraju svoje poteze i kao rezultat mu
    // se vraca gameState kada opet on dodje na red
    @Override
    public DTO doActionTrain(DoActionTrainRequestDTO dto, String token) {
        // treba igrac da posalje sta zeli da uradi i da mu se vrati gameState kao response
        Claims claims = tokenService.parseToken(token);
        //Dohvata usera koji je poslao zahtev.
        Player player = null;
        for (User u : userService.getUsers()) {
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
            }
        }

        if (player == null) {
            //Ne dešava se zato što je igrač već morao da prodje autorizaciju.
            LOG.info("Igrač ne postoji.");
            return new ErrorResponseDTO("Igrač ne postoji.");
        }

        //Dohvate igru u kojoj je igrač
        Game game = gamesTraining.get(player.getCurrGameId()); //ako je igra gotova, pokusace da dohvati ID -1 sto ne posotji i ovde ce se izaci
        //Proverava da li je igrač pristupio igri.
        if (game == null) {
            LOG.info("Greska u train metodi, igra ne postoji.");
            return new ErrorResponseDTO("Greska u train metodi, igra ne postoji.");
        }

        synchronized (game) {
            if (game.getActiveDoActionTrainCall()) {
                LOG.info("Greska pri pokusaju train akcije, vec postoji aktivan poziv.");
                return new ErrorResponseDTO("Greska pri pokusaju train akcije, vec postoji aktivan poziv.");
            }


            game.setActiveDoActionTrainCall(true); // POSTAVLJANJE FLAG-A DA VEC POSTOJI AKTIVAN POZIV

            if (!game.isGameStarted()) {
                game.getTimer().schedule(game.getTimer().task, 0, 1000);
                game.setGameStarted(true);
            }
        }

        try {
            JsonNode node = mapper.readValue(game.getGameState(), JsonNode.class);
            if (game.getTime() == 0) {
                endGame(player.getCurrGameId(), true);
                return new GameEndResponseDTO("Trening igra je završena.");
            }
        } catch (Exception ex) {
            return new ErrorResponseDTO("Greška pri parsiranju JSON-a gameState-a.");
        }

        //deo za formiranje akcije
        String action = dto.getAction();

        ObjectNode trainAction = logicService.trainAction(player.getCurrGameId(), action);
        String message = trainAction.get("message").asText();
        String gameState = trainAction.get("gameState").asText();
        game.setGameState(gameState);
        try {
            socketService.notifySubscribed(game);
        } catch (IOException e) {
            return new ErrorResponseDTO("Greška pri WebSocket komunikaciji");
        }
        synchronized (game) {
            game.setActiveDoActionTrainCall(false); // SKIDANJE FLAG-A DA VEC POSTOJI AKTIVAN POZIV
        }
        if (!message.equals("null")) {
            return new ErrorResponseDTO(message);
        }
        return new DoActionTrainResponseDTO(game.getGameState());
    }

    private int getAvailableKey() {
        LOG.info("Running games:" + games.keySet()); // TODO debug log
        LOG.info("Id u reuseID:" + reuseKeys);  // TODO debug log
        if (reuseKeys.isEmpty()) {
            return highestUnusedKey++;
        } else {
            return reuseKeys.poll();
        }
    }

    private int getAvailableKeyTrain() {
        LOG.info("Running games:" + gamesTraining.keySet()); // TODO debug log
        LOG.info("Id u reuseID:" + reuseKeysTraining);  // TODO debug log
        if (reuseKeysTraining.isEmpty()) {
            return highestUnusedKeyTraining++;
        } else {
            return reuseKeysTraining.poll();
        }
    }

    /**
     * Removes game from list of games, makes that ID available again, removes players from game and sets their gameID field to -1; Sets currPlayer to null
     *
     * @param training True for deleting training games, default False
     */
    public DTO endGame(int gameID, boolean training) {
        Game game = (training ? gamesTraining : games).get(gameID);
        if (game == null) return new DeleteGameResponseDTO("Ne postoji game sa Id-ijem: " + gameID);
        for (Player player : game.getPlayers()) {
            player.setCurrGameId(-1);
            player.setCurrGameIdx(-1);
        }
        game.getTimer().cancel();
        game.setPlayersJoined(0);
        game.getPlayers().clear();
        game.setCurrPlayer(null);
        (training ? gamesTraining : games).remove(gameID);
        (training ? reuseKeysTraining : reuseKeys).offer(gameID);
        logicService.removeGame(gameID, training);
        LOG.info("Removed " + (training ? "training " : "") + "game:" + gameID + (training ? "training" : "")); // TODO debug log
        LOG.info("Running " + (training ? "training " : "") + "games:" + (training ? gamesTraining : games).keySet()); // TODO debug log
        return new DeleteGameResponseDTO("Game sa Id-ijem " + gameID + " je uspešno izbrisan.");
        //game.setTime(0);
    }

    /**
     * FOR NORMAL GAMES; Removes game from list of games, makes that ID available again, removes players from game and sets their gameID field to -1; Sets currPlayer to null
     */
    private void endGame(int gameID) {
        endGame(gameID, false);
    }


    private void endGameTraining(int gameID) {
        Game game = gamesTraining.get(gameID);
        for (Player player : game.getPlayers()) {
            player.setCurrGameId(-1);
            player.setCurrGameIdx(-1);
        }
        game.setPlayersJoined(0);
        game.getPlayers().clear();
        game.setCurrPlayer(null);
        gamesTraining.remove(gameID);
        reuseKeysTraining.offer(gameID);
        logicService.removeGame(gameID, true);

    }


}
