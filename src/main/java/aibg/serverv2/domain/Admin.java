package aibg.serverv2.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Admin extends User{
    public Admin(String username, String password) {
        super(username, password);
    }
}
