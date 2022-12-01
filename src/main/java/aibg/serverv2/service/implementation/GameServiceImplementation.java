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
import java.util.Arrays;
import java.util.TreeMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;


@Service
@Getter
@Setter
public class GameServiceImplementation implements GameService {
    //Not autowired -- ne ide u konstruktor
    private Logger LOG = LoggerFactory.getLogger(GameService.class);
    private Map<Integer, Game> games = new TreeMap<>();
    private PriorityQueue<Integer> reuseKeys= new PriorityQueue<>();
    private Map<Integer, Game> gamesTraining = new TreeMap<>();
    private PriorityQueue<Integer> reuseKeysTraining = new PriorityQueue<>();
    private int highestUnusedKey = 0;
    private int highestUnusedKeyTraining = 0;

    private ObjectMapper mapper = new ObjectMapper();
    private static final long GAME_JOIN_TIMEOUT = 100000;
    private static final long MOVE_TIMEOUT = 500000; //bilo 500
    private static final long UPDATE_TIMEOUT = 150000; //bilo 1500
    //Autowired -- ide u konstruktor
    private LogicService logicService;
    private TokenService tokenService;
    private UserService userService;
    private WebSocketService socketService;
    private Timer timer;

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
        //Dohvata početno stanje igre
        String gameState = logicService.initializeGame(dto);
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici");
        }
        int gameID=getAvailableKey();

        //Proverava da li već postoji igra sa zadatim ID-om.
        if (games.containsKey(gameID)) {
            LOG.info("Igra sa gameId:" + gameID + "već postoji.");
            return new ErrorResponseDTO("Igra sa gameId:" + gameID + "već postoji.");
        }
        Game game = new Game(gameID);
        game.setGameState(gameState);
        //Postavlja vreme trajanja igre i inicijalizuje timer
        game.setTime(dto.getTime() * 60 * 1000);
        timer = new Timer(game, socketService);
        //Dodaje igrače u igru, postavlja im index-e.
        List<Player> players = userService.addPlayers(dto.getPlayerUsernames(),gameID);
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
                timer.schedule(timer.task, 0, 1000);
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
        Game game = games.get(player.getCurrGameId());      //ako je igra gotova, pokusace da dohvati ID -1 sto ne posotji i ovde ce se izaci
        //Proverava da li je igrač pristupio igri.
        if (game == null) {
            LOG.info("Igrač nije pristupio igri ili igra ne postoji.");
            return new ErrorResponseDTO("Niste pristupili igri, ili pokšuvate da igrate u igri koja ne postji.");
        }

        // CITAJ GAZI!!!!
        //izbacio bih ovo skroz: ako nije na njemu da igra, odbacuje se paket i moze da mu se salje odgovor da nije red na njega
        //kad bude red na njega dobice gameSTate
        //a i ovo ostalo nam ne treba - ovo prebacivanje u brojeve i sve nicem ne sluzi, bukv se ne koristi nigde
        //bukv ceo try blok izbaciti, ostaviti samo poziv logike ako je odgovarajuci plejer pozvao ovu funkciju
        if (true) {
            //Dohvata broj igrača u game-u pre poteza.
            int noOfPlayers = game.getPlayers().size();
            //Postavlja nov gameState nakon izvšrenog poteza.
            ObjectNode actionNode = logicService.doAction(player.getCurrGameIdx(), dto.getAction(), player.getCurrGameId());
            String message = "null";
            String gameState;
            String playerAttack;
            if (actionNode != null) {
                message = actionNode.get("message").asText();
                gameState = actionNode.get("gameState").asText();
                playerAttack = actionNode.get("playerAttack").asText();
                game.setGameState(gameState);
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
                if (game.getTime() == 0) {
                    //TODO izbaciti igru iz mape, i izbaciti currGameId i currGameIdx iz igraca,
                    // da ne bi mogli da pristupe novoj igri sa istim Id-ijem
                    // zar ne treba i ovde tajmer da ide / da se proverava
                    endGame(player.getCurrGameId());
                    return new GameEndResponseDTO("Igra je završena.");
                }
                //Iz JSON-a gameState-a dohvata listu igrača i transformiše je u oblik 1,2,...,n
                /*String playerIdxString = node.get("players").toString().replaceAll("^\\[|]$", "");
                List<String> playersString = Arrays.asList(playerIdxString.split(","));
                //Pretvara listu iz Stringov-a u Integer-e -- hehe ide jako funkcionalno ;)
                List<Integer> players = playersString.stream().map(Integer::parseInt).toList();

                //Proverava da li je broj igrača isti kao u prethodnom gameState-u.
                if (noOfPlayers != players.size()) {
                    //Ako nije, pronalazi koji igrač je ispao i izbacuje ga.
                    for (Player p : game.getPlayers()) {
                        if (!players.contains(p.getCurrGameIdx())) {
                            game.remove(p);
                        }
                    }
                }*/
            } catch (Exception ex) {
                return new ErrorResponseDTO("Greška pri parsiranju JSON-a gameState-a.");
            }

            //Pomera na sledećeg igrača
            game.next();

            //nzm da li ovde vratiti error poruku iz logike
            if (!message.equals("null")) {
                return new ErrorResponseDTO(message);
            }

            //Čeka da igrač ponovo dodje na red, i tada mu vraća update-ovan gameState.
            if (waitForUpdate(player)) {
                return new DoActionResponseDTO(game.getGameState());
            } else {
                //Proverava da li je igrač izbačen u medjuvremenu, ako jeste zato nije dočekao potez.
                if (!game.getPlayers().contains(player)) {
                    return new GameEndResponseDTO("Izgubili ste.");
                }

                //Ako je i dalje u igri, a nije dočekao potez, znači da je trenutni igrač time-out-ovao.
                //Izbacuje igrača iz gameState-a i serverske Game klase.
                game.setGameState(logicService.removePlayer(game.getCurrPlayer().getCurrGameIdx(), game.getGameState()));
                game.remove(game.getCurrPlayer());

                return new ErrorResponseDTO("Igrač timeout-ovao dok je čekao update.");
            }
        } else {
            //Proverava da li je igrač izbačen u medjuvremenu, ako jeste zato nije dočekao potez.
            if (!game.getPlayers().contains(player)) {
                return new GameEndResponseDTO("Izgubili ste.");
            }
            //Ako je i dalje u igri, a nije dočekao potez, znači da je trenutni igrač time-out-ovao.
            //Izbacuje igrača iz gameState-a i serverske Game klase.
            game.setGameState(logicService.removePlayer(game.getCurrPlayer().getCurrGameIdx(), game.getGameState()));
            game.remove(game.getCurrPlayer());

            return new ErrorResponseDTO("Igrač timeout-ovao dok je čekao potez.");
        }
    }

    public DTO watchGame(int gameId) {
        Game game = games.get(gameId);
        if (game != null) {
            return new WatchGameResponseDTO(game.getGameState(), game.getTime(), game.getPlayerAttack());
        } else {
            return new ErrorResponseDTO("Greška pri gledanju partije");
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
                Thread.sleep(100);
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
                Thread.sleep(100);
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

    private boolean waitForUpdate(Player p) {
        long start = System.currentTimeMillis();

        //Proverava da li postoji game.
        Game game = games.get(p.getCurrGameId());
        if (game == null) {
            //Ne bi smelo da se desi pošto se proverava null u joinGame metodi.
            return false;
        }
        // Trebalo bi i ovde da bude !, jer treba da ceka dok ne dodje red na njega da pokupi gameState pre svog poteza
        while (game.getCurrPlayer().equals(p)) {
            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                //Ne bi trebalo da se desi.
                LOG.info(ex.getMessage());
            }
            if (System.currentTimeMillis() > start + UPDATE_TIMEOUT) {
                //Igrač predugo čeka na potez, dolazi do timeout-a.
                LOG.info("Igrač timeout-ovao dok je čekao početak sesije.");
                return false;
            }
        }

        return true;
    }


    // Inicijalizuje igru za trening i stavlja je u games
    @Override
    public DTO train(TrainPlayerRequestDTO dto, String token) {

        int currTrainGameId = getAvailableKeyTrain();
        Claims claims = tokenService.parseToken(token);
        //Dohvata usera koji je poslao zahtev.
        Player player = null;
        for (User u : userService.getUsers()) {
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
            }
        }
        if (player == null) { // ako je igrac poslao los token
            LOG.info("Greska u train metodi, igrač ne postoji.");
            return new ErrorResponseDTO("Greska u train metodi, igrač ne postoji.");
        }

        String gameState = logicService.initializeTrainGame("finalMap.txt", currTrainGameId, dto.getPlayerIdx(), player.getUsername());
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici usled kreiranja train igre");
        }
        Game game = new Game(currTrainGameId);

        game.setGameState(gameState);

        //postavlja vreme i pravi novi tajmer
        game.setTime(dto.getTime() * 60 * 1000);
        timer = new Timer(game, socketService);


        //Dodaje igrača u igru, postavlja mu index.
        player.setCurrGameId(game.getGameId());
        player.setCurrGameIdx(dto.getPlayerIdx());

        //Uspešno kreiran game, dodaje se u listu postojećih sesija i vraca poruku o uspesno napravljenom game-u
        gamesTraining.put(currTrainGameId, game);
        return new TrainPlayerResponseDTO("TrainingGame sa id-ijem: " + game.getGameId() + "uspešno napravljen.");


    }



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

        if (game.getActiveDoActionTrainCall()) {
            LOG.info("Greska pri pokusaju train akcije, vec postoji aktivan poziv.");
            return new ErrorResponseDTO("Greska pri pokusaju train akcije, vec postoji aktivan poziv.");
        }

        game.setActiveDoActionTrainCall(true); // POSTAVLJANJE FLAG-A DA VEC POSTOJI AKTIVAN POZIV

        if (!game.isGameStarted()) {
            timer.schedule(timer.task, 0, 1000);
            game.setGameStarted(true);
        }

        try {
            JsonNode node = mapper.readValue(game.getGameState(), JsonNode.class);
            if (game.getTime() == 0) {
            //TODO izbaciti igru iz mape, i izbaciti currGameId i currGameIdx iz igraca,
            // da ne bi mogli da pristupe novoj igri sa istim Id-ijem
            // TODO: odredi ko je pobednik, vrati to igracu ili ne, odradi sta vec treba da se odradi na kraju igre
            // TODO: nakon sto se trening igra zavrsi da se reciklira/izbaci iz games mape!
            endGameTraining(player.getCurrGameId());
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

        game.setActiveDoActionTrainCall(false); // SKIDANJE FLAG-A DA VEC POSTOJI AKTIVAN POZIV
        if (!message.equals("null")) {
            return new ErrorResponseDTO(message);
        }
        return new DoActionTrainResponseDTO(game.getGameState());
    }

    private int getAvailableKey(){
        if(reuseKeys.isEmpty()){
            return highestUnusedKey++;
        }else{
            return reuseKeys.poll();
        }
    }

    private int getAvailableKeyTrain(){
        if(reuseKeysTraining.isEmpty()){
            return highestUnusedKeyTraining++;
        }else{
            return reuseKeysTraining.poll();
        }
    }


    //treba se pozove odakle god odlucimo da zavrsimo igru
    /** Removes game from list of games, makes that ID available again, removes players from game and sets their gameID field to -1
     */
    private void endGame(int gameID){
        Game game = games.get(gameID);
        for(Player player : game.getPlayers()){
            player.setCurrGameId(-1);
            player.setCurrGameIdx(-1);
        }
        game.setPlayersJoined(0);
        game.setPlayers(null);
        games.remove(gameID);
        reuseKeys.offer(gameID);
        logicService.removeGame(gameID,false);
    }

    private void endGameTraining(int gameID){
        Game game = gamesTraining.get(gameID);
        for(Player player : game.getPlayers()){
            player.setCurrGameId(-1);
            player.setCurrGameIdx(-1);
        }
        game.setPlayersJoined(0);
        game.setPlayers(null);
        gamesTraining.remove(gameID);
        reuseKeysTraining.offer(gameID);
        logicService.removeGame(gameID,true);

    }
}
}