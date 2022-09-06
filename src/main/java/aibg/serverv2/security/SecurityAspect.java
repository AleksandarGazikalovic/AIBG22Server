package aibg.serverv2.security;


import aibg.serverv2.domain.Admin;
import aibg.serverv2.domain.User;
import aibg.serverv2.dto.ErrorResponseDTO;
import aibg.serverv2.service.TokenService;
import aibg.serverv2.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.Getter;
import lombok.Setter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Configuration
@Getter
@Setter
public class SecurityAspect {
    //Not Autowired -- ne ide u konstruktor
    private Logger LOG = LoggerFactory.getLogger(SecurityAspect.class);
    private String key = "secret_key";
    //Autowired -- ide u konstruktor
    private TokenService tokenService;
    private UserService userService;

    @Autowired
    public SecurityAspect(TokenService tokenService, UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    @Around("@annotation(aibg.serverv2.security.CheckSecurity)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        //Dohvata podatke o metodi u kojoj je joinPoint.
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();

        //Proverava da li token postoji, i ako postoji dohvata ga.
        String token = null;
        for (int i = 0; i < methodSignature.getParameterNames().length; i++) {
            if (methodSignature.getParameterNames()[i].equals("authorization")) {
                //Proverava da li se postuje Bearer scheme
                if (joinPoint.getArgs()[i].toString().startsWith("Bearer")) {
                    //Dohvata token
                    token = joinPoint.getArgs()[i].toString().split(" ")[1];
                }
            }
        }

        //Ako token ne moze da se dohvati -> nije autorizovan zahtev.
        if(token == null){
            LOG.info("Token nije pronadjen u potpisu funkcije.");
            return new ResponseEntity<>(new ErrorResponseDTO("Token nije pronadjen u potpisu funkcije."),HttpStatus.UNAUTHORIZED);
        }

        //Parsira token.
        Claims claims = tokenService.parseToken(token);

        //Ako ne može da parsira token -> nije autorizovan zahtev.
        if (claims == null) {
            return new ResponseEntity<>(new ErrorResponseDTO("Nije moguće generisati Claims objekat")
                    ,HttpStatus.UNAUTHORIZED);
        }

        //Dohvata tipove Usera koji mogu da pozovu zadatu metodu.
        CheckSecurity checkSecurity = method.getAnnotation(CheckSecurity.class);

        //Dohvata usera koji je poslao zahtev
        User user = null;
        for(User u :userService.getUsers()){
            if(u.getUsername().equals(claims.get("username")) &&
                    u.getPassword().equals(claims.get("password"))){
                user = u;
            }
        }
        //Proverava da li postoji dati User, ne bi trebalo da se dešava
        //pošto mora da postoji da bi dobio token.
        if(user == null){
            return new ResponseEntity<>(new ErrorResponseDTO("Ne postoji user definisan datim tokenom."),
                    HttpStatus.BAD_REQUEST);
        }

        //Postavlja tip User-a.
        String type = (user instanceof Admin) ? "A" : "P";

        //Ako je dozvoljen tip, nastavlja izvršvanje.
        if (Arrays.asList(checkSecurity.roles()).contains(type)) {
            return joinPoint.proceed();
        }

        //Ako nije, nije dozvoljeno izvršavaje.
        return new ResponseEntity<>(new ErrorResponseDTO("Nije vam dozvoljeno slanje traženog zahteva."),
                HttpStatus.FORBIDDEN);
    }
}
