package aibg.serverv2.service;

import io.jsonwebtoken.Claims;

public interface TokenService {
    //Uzima set claim-ova(polja koja se hash-iraju u token) i vraća token.
    String generate(Claims claims);

    //Uzima token i vraća set claim-ov (informacije koje se nalaze u tokenu).
    Claims parseToken(String jwt);
}
