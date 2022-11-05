package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
import aibg.serverv2.configuration.Timer;
import aibg.serverv2.domain.Game;
import aibg.serverv2.domain.Player;
import aibg.serverv2.domain.User;
import aibg.serverv2.dto.*;
import aibg.serverv2.service.GameService;
import aibg.serverv2.service.LogicService;
import aibg.serverv2.service.TokenService;
import aibg.serverv2.service.UserService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Getter
@Setter
public class GameServiceImplementation implements GameService {
    //Not autowired -- ne ide u konstruktor
    private Logger LOG = LoggerFactory.getLogger(GameService.class);
    private Map<Integer, Game> games = new HashMap<>();
    private ObjectMapper mapper = new ObjectMapper();
    private static final long GAME_JOIN_TIMEOUT = 100000;
    private static final long MOVE_TIMEOUT = 500000; //bilo 500
    private static final long UPDATE_TIMEOUT = 150000; //bilo 1500
    //Autowired -- ide u konstruktor
    private LogicService logicService;
    private TokenService tokenService;
    private UserService userService;
    private Timer timer;

    @Autowired
    public GameServiceImplementation(LogicService logicService, TokenService tokenService, UserService userService) {
        this.logicService = logicService;
        this.tokenService = tokenService;
        this.userService = userService;
    }

    /*
            Kreira novu instancu Game objekta za parametre zadate u @CreateGameDTO
            i dodaje ga na listu aktivnih sesija.
         */
    @Override
    public DTO createGame(CreateGameRequestDTO dto) {
        //Dohvata početno stanje igre
        String gameState = logicService.initializeGame(dto.getGameId(), dto.getMapName());
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici");
        }
        Game game = new Game(dto.getGameId());
        //Proverava da li već postoji igra sa zadatim ID-om.
        if (games.containsKey(dto.getGameId())) {
            LOG.info("Igra sa gameId:" + game.getGameId() + "već postoji.");
            return new ErrorResponseDTO("Igra sa gameId:" + game.getGameId() + "već postoji.");
        }
        game.setGameState(gameState);
        //Postavlja vreme trajanja igre i inicijalizuje timer
        game.setTime(dto.getTime() * 60 * 1000);
        timer = new Timer(game);
        //Dodaje igrače u igru, postavlja im index-e.
        List<Player> players = userService.addPlayers(dto.getPlayerUsernames(), dto.getGameId());
        if (players == null) {
            return new ErrorResponseDTO("Greška pri dodavanju igrača u igru.");
        }
        game.setPlayers(players);
        //Postavlja početnog igrača.
        game.setCurrPlayer(players.get(0));

        //Uspešno kreiran game, dodaje se u listu postojećih sesija, i vraća se odgovor.
        games.put(game.getGameId(), game);
        return new CreateGameResponseDTO("Game sa id:" + game.getGameId() + "uspešno napravljen.");
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
        Game game = games.get(player.getCurrGameId());
        //Proverava da li je igrač pristupio igri.
        if (game == null) {
            LOG.info("Igrač nije pristupio igri ili igra ne postoji.");
            return new ErrorResponseDTO("Niste pristupili igri, ili pokšuvate da igrate u igri koja ne postji.");
        }


        if (waitForMyMove(player)) {
            //Dohvata broj igrača u game-u pre poteza.
            int noOfPlayers = game.getPlayers().size();
            //Postavlja nov gameState nakon izvšrenog poteza.
            ObjectNode actionNode = logicService.doAction(player.getCurrGameIdx(), dto.getAction(), player.getCurrGameId());
            String message = actionNode.get("message").asText();
            String gameState = actionNode.get("gameState").asText();
            String players = actionNode.get("players").asText();
            game.setGameState(gameState);


            //Dohvata preostale igrače nakon poteza.
            try {
                JsonNode node = mapper.readValue(game.getGameState(), JsonNode.class);
                //Proverava da li je postoji pobednik. TODO testirati kako se vraća null string.
                //testirati da li je okej, vrv treba asText();
                String winner = node.get("winner").toString();
                if (winner != null && game.getTime() == 0) {
                    //TODO izbaciti igru iz mape, i izbaciti currGameId i currGameIdx iz igraca,
                    // da ne bi mogli da pristupe novoj igri sa istim Id-ijem
                    return new GameEndResponseDTO("Igra je završena, pobednik je: " + winner + ".");
                } else if (winner == null && game.getTime() == 0) {
                    return new ErrorResponseDTO("Igra je završena bez pobednika.");
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
                return new DoActionResponseDTO(game.getGameState(), players);
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

        int currTrainGameId = 10;
        for (int i = 10; i < 100000; i++) {
            if (!games.containsKey(i)) {
                currTrainGameId = i;
                break;
            }
        }

        String gameState = logicService.initializeTrainGame("finalMap.txt", currTrainGameId, dto.getPlayerIdx());
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici usled kreiranja train igre");
        }
        Game game = new Game(currTrainGameId);

        game.setGameState(gameState);

        //postavlja vreme i pravi novi tajmer
        game.setTime(dto.getTime() * 60 * 1000);
        timer = new Timer(game);

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

        //Dodaje igrača u igru, postavlja mu index.
        player.setCurrGameId(game.getGameId());
        player.setCurrGameIdx(dto.getPlayerIdx());

        //Uspešno kreiran game, dodaje se u listu postojećih sesija i vraca poruku o uspesno napravljenom game-u
        games.put(currTrainGameId, game);
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
        Game game = games.get(player.getCurrGameId());
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
            String winner = node.get("winner").toString();
            if (winner != null && game.getTime() == 0) {
                //TODO izbaciti igru iz mape, i izbaciti currGameId i currGameIdx iz igraca,
                // da ne bi mogli da pristupe novoj igri sa istim Id-ijem
                return new GameEndResponseDTO("Trening igra je završena, pobednik je: " + winner + ".");
            } else if (winner == null && game.getTime() == 0) {
                return new ErrorResponseDTO("Trening igra je završena bez pobednika.");
            }
        } catch (Exception ex) {
            return new ErrorResponseDTO("Greška pri parsiranju JSON-a gameState-a.");
        }

        //deo za formiranje akcije
        String action = dto.getAction();

        ObjectNode trainAction = logicService.trainAction(player.getCurrGameId(), action);
        String message = trainAction.get("message").asText();
        String gameState = trainAction.get("gameState").asText();
        String players = trainAction.get("players").asText();
        game.setGameState(gameState);

        game.setActiveDoActionTrainCall(false); // SKIDANJE FLAG-A DA VEC POSTOJI AKTIVAN POZIV
        if (!message.equals("null")) {
            return new ErrorResponseDTO(message);
        }
        return new DoActionTrainResponseDTO(game.getGameState(), players);
    }
    
}

