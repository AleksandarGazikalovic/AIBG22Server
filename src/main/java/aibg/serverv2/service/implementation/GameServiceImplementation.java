package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
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
import io.jsonwebtoken.Claims;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Arrays;
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
    private static final long GAME_JOIN_TIMEOUT = 10000;
    private static final long MOVE_TIMEOUT = 500;
    private static final long UPDATE_TIMEOUT = 1500;
    //Autowired -- ide u konstruktor
    private LogicService logicService;
    private TokenService tokenService;
    private UserService userService;

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
    public DTO createGame(CreateGameReqeustDTO dto) {
        Game game = new Game(dto.getGameId());
        //Proverava da li već postoji igra sa zadatim ID-om.
        if (games.containsKey(dto.getGameId())) {
            LOG.info("Igra sa gameId:" + game.getGameId() + "već postoji.");
            return new ErrorResponseDTO("Igra sa gameId:" + game.getGameId() + "već postoji.");
        }
        //Dohvata početno stanje igre
        String gameState = logicService.initializeGame();
        if (gameState == null) {
            return new ErrorResponseDTO("Greška u logici");
        }
        game.setGameState(gameState);

        //Dodaje igrače u igru, postavlja im index-e.
        List<Player> players = userService.addPlayers(dto.getPlayerUsernames());
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
    public DTO joinGame(JoinGameRequestDTO dto, String token) {
        Claims claims = tokenService.parseToken(token);
        //Dohvata usera koji je poslao zahtev.
        Player player = null;
        for (User u : userService.getUsers()) {
            if (u.getUsername().equals(claims.get("username"))) {
                player = (Player) u;
            }
        }

        if(player == null){
            //Ne dešava se zato što je igrač već morao da prodje autorizaciju.
            LOG.info("Igrač ne postoji.");
            return new ErrorResponseDTO("Igrač ne postoji.");
        }

        //Dohvata Game na koji igrač pokušava da se prikači.
        Game game = games.get(dto.getGameId());

        //Proverava da li postoji Game sa datim ID-om.
        if (game == null) {
            LOG.info("Pokušano da se prikači na nepostojeću igru.");
            return new ErrorResponseDTO("Igra sa id: " + dto.getGameId() + " ne postoji.");
        }

        //Poverava da li se igrač nalazi u datoj igri, i ako se nalazi ubacuje ga.
        if (!game.getPlayers().contains(player)) {
            LOG.info("Igrač pokušao da se priključi igri kojoj ne pripada.");
            return new ErrorResponseDTO("Ne nalazite se u igri kojoj pokušavate da se priključite.");
        }

        //Dodaje igraču gameId.
        player.setCurrGameId(dto.getGameId());

        if (waitForGame(dto.getGameId())) {
            return new JoinGameResponseDTO(logicService.getPlayerView(player.getCurrGameIdx(), game.getGameState()));
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

        if(player == null){
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

        //Kreira zahtev koji logika zna da parsira.
        String action = "{\"Player\":\"" + player.getCurrGameIdx()
                + "\",\"Action\":\"" + dto.getAction() + "\"}|"
                + game.getGameState();

        if (waitForMyMove(player)) {
            //Dohvata broj igrača u game-u pre poteza.
            int noOfPlayers = game.getPlayers().size();
            //Postavlja nov gameState nakon izvšrenog poteza.
            game.setGameState(logicService.doAction(action, game.getGameState()));

            //Dohvata preostale igrače nakon poteza.
            try {
                JsonNode node = mapper.readValue(game.getGameState(), JsonNode.class);
                //Proverava da li je postoji pobednik. TODO testirati kako se vraća null string.
                String winner = node.get("winner").toString();
                if (winner != null) {
                    return new GameEndResponseDTO("Igra je završena, pobednik je: " + winner + ".");
                }
                //Iz JSON-a gameState-a dohvata listu igrača i transformiše je u oblik 1,2,...,n
                String playerIdxString = node.get("players").toString().replaceAll("^\\[|]$", "");
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
                }
            } catch (Exception ex) {
                return new ErrorResponseDTO("Greška pri parsiranju JSON-a gameState-a.");
            }

            //Pomera na sledećeg igrača
            game.next();

            //Čeka da igrač ponovo dodje na red, i tada mu vraća update-ovan gameState.
            if (waitForUpdate(player)) {
                return new DoActionResponseDTO(logicService.getPlayerView(player.getCurrGameIdx(), game.getGameState()));
            }else{
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

        while (game.getCurrPlayer().equals(p)) {
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
}

/*

    zahtev za pocetno stanje -> dobiju pocetno /play
    Game = pocetno stanje

    i = 0
    while(uslov == true)
        {
            iz Game -> izracunaj potez
            if i = 0
                posalji zahtev za pocetnu
                i++
            zahtev za potez -> dobiju novo stanje
            Game = novo stanje
            continue
        }
 */
