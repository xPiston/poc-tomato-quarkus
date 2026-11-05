package fr.dllv.booking.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Un jeton Bearer, emis a la connexion et detruit a la deconnexion. */
@Entity
@Table(name = "api_tokens")
public class ApiToken {

    @Id
    @Column(length = 64)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Requis par JPA. */
    protected ApiToken() {}

    public ApiToken(User user, String token, OffsetDateTime createdAt) {
        this.user = user;
        this.token = token;
        this.createdAt = createdAt;
    }

    public String token() {
        return token;
    }

    public User user() {
        return user;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
