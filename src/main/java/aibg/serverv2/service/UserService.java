package aibg.serverv2.service;

import aibg.serverv2.domain.Player;
import aibg.serverv2.domain.User;
import aibg.serverv2.dto.DTO;
import aibg.serverv2.dto.LoginRequestDTO;

import java.util.List;

public interface UserService {
    //Prima username i password korisnika, i vraća token
    DTO login(LoginRequestDTO dto);

    //Dohvata sve User-e, mora da se koristi zato što se interface prosledjuje kroz konstruktore.
    List<User> getUsers();

    //Dodaje igrače na konkretnu sesiju, i zadaje im index-e.
    List<Player> addPlayers(List<String> usernames);
}
