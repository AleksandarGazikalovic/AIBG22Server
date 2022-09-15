package aibg.serverv2.service.implementation;

import aibg.serverv2.configuration.Configuration;
import aibg.serverv2.domain.Admin;
import aibg.serverv2.domain.Player;
import aibg.serverv2.domain.User;
import aibg.serverv2.dto.DTO;
import aibg.serverv2.dto.ErrorResponseDTO;
import aibg.serverv2.dto.LoginRequestDTO;
import aibg.serverv2.dto.LoginResponseDTO;
import aibg.serverv2.service.TokenService;
import aibg.serverv2.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Getter
@Setter
public class UserServiceImplementation implements UserService {
    //Not Autowired -- ne idu u konstruktor
    private Logger LOG = LoggerFactory.getLogger(UserServiceImplementation.class);
    private List<User> users = new ArrayList<>();
    //Autowired -- idu u konstruktor
    private TokenService tokenService;

    @Autowired
    public UserServiceImplementation(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /*
            Proverava da li postoji igrač sa username-om i password-om definisanim u
                @LoginRequestDTO
            i ako postoji vraća token koji se koristi za dalju interakciju sa serverom.
            Ako igrač ne postoji vraća @ErrorResponseDTO
         */
    @Override
    public DTO login(LoginRequestDTO dto) {
        for(User user : users){
            if(user.getUsername().equals(dto.getUsername()) &&
                    user.getPassword().equals(dto.getPassword())){
                //Generiše polja koja će se hash-irati u token.
                Claims claims = Jwts.claims();
                claims.put("username", dto.getUsername());
                claims.put("password", dto.getPassword());

                String token = tokenService.generate(claims);
                if(token == null){
                    //Ne bi trebalo da se dešava.
                    return new ErrorResponseDTO("Token nije uspešno generisan.");
                }

                return new LoginResponseDTO(token);
            }
        }
        LOG.info("Igrač sa username-om: " + dto.getUsername() + " i password-om: " +
                dto.getPassword() + " ne postoji.");
        return new ErrorResponseDTO("Igrač sa username-om: " + dto.getUsername() + " i password-om: " +
                dto.getPassword() + " ne postoji.");
    }

    //Randomizuje pozicije igrača, zadaje im indekse i dodaje ih u igru.
    @Override
    public List<Player> addPlayers(List<String> usernames, int gameId) {
        //Dodaje igrače u igru
        List<Player> players = new ArrayList<>();
        for(String name : usernames){
            for(User u : users){
                if(u.getUsername().equals(name)){
                    if(u instanceof Admin){
                        LOG.info("Admin ne može da učestvuje u igri.");
                        return null;
                    }
                    players.add((Player) u);
                }
            }
        }
        //Ako nije nadjeno dovoljno igrača, nije dobra igra
        if(players.size() != Configuration.noOfPlayers){
            LOG.info("Nije pronadjeno dovojno igrača.");
            return null;
        }
        //Randomizuje igrače i dodaje ih na igru.
        Collections.shuffle(players);

        //Dodaje igračima index-e
        for(Player p : players){
            p.setCurrGameIdx(players.indexOf(p)+1);
            p.setCurrGameId(gameId);
        }

        return players;
    }
}
