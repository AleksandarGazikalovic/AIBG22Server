package aibg.serverv2.configuration;

import aibg.serverv2.domain.Admin;
import aibg.serverv2.domain.Player;
import aibg.serverv2.service.UserService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Getter
@Setter
public class Configuration {
    //Not Autowired -- ne idu u konstruktor
    private Logger LOG = LoggerFactory.getLogger(Configuration.class);
    private File configFile = new File("src/main/resources/configuration");
    private List<String> usernames = new ArrayList<>();
    private List<String> passwords = new ArrayList<>();
    private List<String> types = new ArrayList<>();
    public static int noOfPlayers;
    public static String logicAddress;
    //Autowired -- idu u konstruktor
    private UserService userService;

    @Autowired
    public Configuration(UserService userService) {
        this.userService = userService;
    }

    //Parsira konfiguracioni file
    @EventListener(ApplicationReadyEvent.class)
    public void parse() throws Exception{
        //Postavka sa citanje iz file-a
        FileReader fr = new FileReader(this.configFile);
        BufferedReader br = new BufferedReader(fr);
        String line;

        while((line = br.readLine()) != null) {
            //Parsira liniju konfiguracionog file-a
            String[] keyValuePair = line.split(":");
            String key = keyValuePair[0];
            String value = keyValuePair[1];

            switch (key) {
                case "usernames" -> {
                    String[] usernames = value.split(",");
                    this.usernames.addAll(Arrays.asList(usernames));
                }
                case "passwords" -> {
                    String[] passwords = value.split(",");
                    this.passwords.addAll(Arrays.asList(passwords));
                }
                case "types" -> {
                    String[] types = value.split(",");
                    this.types.addAll(Arrays.asList(types));
                }
                case "noOfPlayers" -> noOfPlayers = Integer.parseInt(value.trim());
                case "logicAddress" -> logicAddress = value;
                default -> LOG.info("Greška pri parsiranju konfiguracionog file-a.");
            }
        }

        this.addUsers();
    }

    //Popunjava UserService sa korisnicima.
    private void addUsers(){
        for(int i = 0;i < usernames.size();i++){
            if(types.get(i).equals("A")){
                userService.getUsers().add(new Admin(usernames.get(i),passwords.get(i)));
            }else{
                userService.getUsers().add(new Player(usernames.get(i),passwords.get(i)));
            }
        }
    }
}
