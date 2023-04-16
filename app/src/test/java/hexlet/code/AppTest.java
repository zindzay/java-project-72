package hexlet.code;

import hexlet.code.domain.Url;
import hexlet.code.domain.query.QUrl;
import io.ebean.DB;
import io.ebean.Database;
import io.javalin.Javalin;
import kong.unirest.Empty;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {
    private static Javalin app;
    private static String baseUrl;
    private static Database database;
    private static MockWebServer mockWebServer;
    private static final String TEMPLATES_DIRECTORY = "src/test/resources/mock-templates";

    @BeforeAll
    public static void beforeAll() {
        app = App.getApp();
        app.start(0);
        int port = app.port();
        baseUrl = "http://localhost:" + port;
        database = DB.getDefault();
    }

    @AfterAll
    public static void afterAll() {
        app.stop();
    }

    @BeforeEach
    void beforeEach() {
        database.script().run("/truncate.sql");
        database.script().run("/seed-test-db.sql");
        mockWebServer = new MockWebServer();
    }

    @AfterEach
    void afterEach() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    class RootTest {
        @Test
        void testIndex() {
            final HttpResponse<String> response = Unirest.get(baseUrl).asString();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getBody()).contains("Бесплатно проверяйте сайты на SEO пригодность");
        }
    }

    @Nested
    class UrlTest {
        @Test
        void testUrls() {
            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains("ID")
                    .contains("Имя")
                    .contains("Последняя проверка")
                    .contains("Код ответа")
                    .contains("https://github.com")
                    .contains("https://www.youtube.com:443");
        }

        @Test
        void testShow() {
            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls/1")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains("Сайт https://github.com")
                    .contains("ID")
                    .contains("Имя")
                    .contains("Дата создания")
                    .contains("01/01/2022 13:57")
                    .contains("ID")
                    .contains("Код ответа")
                    .contains("title")
                    .contains("h1")
                    .contains("Дата проверки");
        }

        @Test
        void testCheck() throws IOException {
            final String mockPage = Files.readString(Paths.get(TEMPLATES_DIRECTORY, "index.html"));
            final String mockPageUrl = mockWebServer.url("/").toString();
            mockWebServer.enqueue(new MockResponse().setBody(mockPage));

            final var url = new Url(mockPageUrl);
            url.save();

            final HttpResponse<String> responsePost = Unirest
                    .post(baseUrl + "/urls/" + url.getId() + "/checks")
                    .asString();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/urls/" + url.getId());

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls/" + url.getId())
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains("ID")
                    .contains("Код ответа")
                    .contains("title")
                    .contains("h1")
                    .contains("Дата проверки")
                    .contains(Objects.toString(url.getId()))
                    .contains("200")
                    .contains(new SimpleDateFormat("dd/MM/yyyy").format(new Date()));
        }

        @Test
        void testCreate() {
            final var inputUrl = "https://www.twitch.tv:443/psherotv";
            final var normalizedUrl = "https://www.twitch.tv:443";
            final HttpResponse<Empty> responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", inputUrl)
                    .asEmpty();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/urls");

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains(normalizedUrl)
                    .contains("Страница успешно добавлена");

            final Url actualUrl = new QUrl()
                    .name.equalTo(normalizedUrl)
                    .findOne();

            assertThat(actualUrl).isNotNull();
            assertThat(actualUrl.getName()).isEqualTo(normalizedUrl);
        }

        @Test
        void testCreateIfAlreadyExists() {
            final var inputUrl = "https://www.twitch.tv/psherotv";
            final var normalizedUrl = "https://www.twitch.tv";
            Unirest.post(baseUrl + "/urls")
                    .field("url", inputUrl)
                    .asEmpty();
            final HttpResponse<Empty> responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", inputUrl)
                    .asEmpty();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/urls");

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains(normalizedUrl)
                    .contains("Страница уже существует");

            final List<Url> actualUrls = new QUrl()
                    .name.equalTo(normalizedUrl)
                    .findList();

            assertThat(actualUrls).hasSize(1);
        }

        @Test
        void testCreateIfBadUrl() {
            final var inputUrl = "badurl";
            final HttpResponse<Empty> responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", inputUrl)
                    .asEmpty();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/");

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body).contains("Некорректный URL");

            final Url actualUrls = new QUrl()
                    .name.equalTo(inputUrl)
                    .findOne();

            assertThat(actualUrls).isNull();
        }
    }
}
