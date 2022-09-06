package aibg.serverv2.service.implementation;

import aibg.serverv2.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Getter
@Setter
public class TokenServiceImplementation implements TokenService {
    private Logger LOG = LoggerFactory.getLogger(TokenServiceImplementation.class);
    private String key = "secret_key";

    /*
        Uzima Claims objekat koji sadrži username i password datog korisnika,
        i na osnovu njih generiše token koji će korisnik koristi za autentifikaciju
        i autorizaciju.
     */
    @Override
    public String generate(Claims claims) {
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512,key)
                .compact();
    }

    /*
        Prevodi token u Claims objekat koji sadrži polja username i password.
     */
    @Override
    public Claims parseToken(String jwt) {
        Claims claims;
        try{
            claims = Jwts.parser()
                    .setSigningKey(key)
                    .parseClaimsJws(jwt)
                    .getBody();
        }catch (Exception e){
            LOG.info(e.getMessage());
            return null;
        }
        if(claims == null){
            LOG.info("Nije moguće generisati Claims objekat");
        }
        return claims;
    }
}
