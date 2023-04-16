package hexlet.code.domain;

import io.ebean.Model;
import io.ebean.annotation.NotNull;
import io.ebean.annotation.WhenCreated;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import java.time.Instant;

@Entity
public final class UrlCheck extends Model {
    @Id
    private long id;

    private Integer statusCode;

    private String title;

    private String h1;

    @Lob
    private String description;

    @NotNull
    @ManyToOne
    private Url url;

    @WhenCreated
    private Instant createdAt;

    public UrlCheck(final Integer statusCode, final String title,
                    final String h1, final String description, final Url url) {
        this.statusCode = statusCode;
        this.title = title;
        this.h1 = h1;
        this.description = description;
        this.url = url;
    }

    public long getId() {
        return id;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getTitle() {
        return title;
    }

    public String getH1() {
        return h1;
    }

    public String getDescription() {
        return description;
    }

    public Url getUrl() {
        return url;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
