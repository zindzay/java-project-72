package hexlet.code;

import hexlet.code.domain.Url;
import hexlet.code.domain.UrlCheck;
import hexlet.code.domain.query.QUrl;
import hexlet.code.domain.query.QUrlCheck;
import io.ebean.DB;
import io.ebean.Transaction;
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
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AppTests {
    private static Javalin app;
    private static final String URL = "https://github.com";
    private static String baseUrl;
    private static Url existingUrl;
    private static UrlCheck existingUrlCheck;
    private static Transaction transaction;
    private static MockWebServer mockServer;

    private static Path getFixturePath(final String fileName) {
        return Paths.get("src", "test", "resources", "fixtures", fileName)
                .toAbsolutePath().normalize();
    }

    private static String readFixture(final String fileName) throws IOException {
        Path filePath = getFixturePath(fileName);
        return Files.readString(filePath).trim();
    }

    @BeforeAll
    public static void beforeAll() throws IOException {
        app = App.getApp();
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
        existingUrl = new QUrl().name.equalTo(URL).findOne();
        existingUrlCheck = new QUrlCheck().url.equalTo(existingUrl).findOne();
        mockServer = new MockWebServer();
        MockResponse mockedResponse = new MockResponse().setBody(readFixture("index.html"));
        mockServer.enqueue(mockedResponse);
        mockServer.start();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        app.stop();
        mockServer.shutdown();
    }

    @BeforeEach
    void beforeEach() {
        transaction = DB.beginTransaction();
    }

    @AfterEach
    void afterEach() {
        transaction.rollback();
    }

    @Nested
    class RootTest {
        @Test
        void testIndex() {
            final HttpResponse<String> response = Unirest.get(baseUrl).asString();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getBody()).contains("Анализатор страниц");
        }
    }

    @Nested
    class UrlTest {
        @Test
        void testShowUrls() {
            HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains(existingUrl.getName())
                    .contains(String.valueOf(existingUrlCheck.getStatusCode()));
        }

        @Test
        void testShowUrl() {
            HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls/" + existingUrl.getId())
                    .asString();
            String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains(existingUrl.getName())
                    .contains(String.valueOf(existingUrlCheck.getStatusCode()));
        }

        @Test
        void testCreate() {
            final String inputUrl = "https://www.youtube.com";
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
                    .contains(inputUrl)
                    .contains("Страница успешно добавлена");

            final Url actualUrl = new QUrl().name.equalTo(inputUrl).findOne();

            assertThat(actualUrl).isNotNull();
            assertThat(actualUrl.getName()).isEqualTo(inputUrl);
        }

        @Test
        void testCreateIfUrlExists() {
            final HttpResponse<Empty> responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", URL)
                    .asEmpty();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/urls");

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .contains(URL)
                    .contains("Страница уже существует");

            final Url actualUrl = new QUrl().name.equalTo(URL).findOne();

            assertThat(actualUrl).isNotNull();
            assertThat(actualUrl.getName()).isEqualTo(URL);
        }

        @Test
        void testCreateIfBadUrl() {
            final String inputUrl = "bad-url";
            final HttpResponse<Empty> responsePost = Unirest
                    .post(baseUrl + "/urls")
                    .field("url", inputUrl)
                    .asEmpty();

            assertThat(responsePost.getStatus()).isEqualTo(302);
            assertThat(responsePost.getHeaders().getFirst("Location")).isEqualTo("/");

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls")
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body)
                    .doesNotContain(inputUrl)
                    .contains("Некорректный URL");

            final Url actualUrl = new QUrl().name.equalTo(inputUrl).findOne();

            assertThat(actualUrl).isNull();
        }

        @Test
        void testChecks() {
            final String url = mockServer.url("/").toString().replaceAll("/$", "");

            Unirest.post(baseUrl + "/urls")
                    .field("url", url)
                    .asEmpty();

            final Url actualUrl = new QUrl().name.equalTo(url).findOne();

            assertThat(actualUrl).isNotNull();
            assertThat(actualUrl.getName()).isEqualTo(url);

            Unirest.post(baseUrl + "/urls/" + actualUrl.getId() + "/checks")
                    .asEmpty();

            final HttpResponse<String> response = Unirest
                    .get(baseUrl + "/urls/" + actualUrl.getId())
                    .asString();
            final String body = response.getBody();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(body).contains("Страница успешно проверена");

            final UrlCheck actualCheckUrl = new QUrlCheck().url.equalTo(actualUrl).findOne();

            assertThat(actualCheckUrl).isNotNull();
            assertThat(actualCheckUrl.getStatusCode()).isEqualTo(200);
            assertThat(actualCheckUrl.getTitle()).isEqualTo("Test page");
            assertThat(actualCheckUrl.getH1()).isEqualTo("Do not expect a miracle, miracles yourself!");
            assertThat(actualCheckUrl.getDescription()).contains("statements of great people");
        }
    }
}
