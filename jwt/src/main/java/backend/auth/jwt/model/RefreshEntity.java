package backend.auth.jwt.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter
public class RefreshEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String refresh;
    private String expiration;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRefresh(String refresh) {
        this.refresh = refresh;
    }

    public void setExpiration(String expiration) {
        this.expiration = expiration;
    }
}
