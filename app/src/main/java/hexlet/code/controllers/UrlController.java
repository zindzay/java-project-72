package hexlet.code.controllers;

import hexlet.code.domain.Url;
import hexlet.code.domain.UrlCheck;
import hexlet.code.domain.query.QUrl;
import hexlet.code.domain.query.QUrlCheck;
import io.ebean.PagedList;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import jakarta.annotation.Nullable;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public final class UrlController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlController.class);

    public static Handler listUrls = ctx -> {
        LOGGER.info("Request urls list.");

        final int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1) - 1;
        final PagedList<Url> pagedUrlsWithChecks = getUrlsWithChecksByPage(page);
        final int totalPage = pagedUrlsWithChecks.getTotalPageCount() + 1;
        final int currentPage = pagedUrlsWithChecks.getPageIndex() + 1;
        final List<Integer> pages = IntStream
                .range(1, totalPage)
                .boxed()
                .toList();

        ctx.attribute("urls", pagedUrlsWithChecks.getList());
        ctx.attribute("pages", pages);
        ctx.attribute("currentPage", currentPage);
        ctx.render("urls.html");
    };


    public static Handler createUrl = ctx -> {
        final String urlFromParams = ctx.formParamAsClass("url", String.class).getOrDefault(null);

        LOGGER.info("Request create url. [url={}]", urlFromParams);

        final String normalizedUrl = getNormalizedUrl(urlFromParams);

        if (normalizedUrl == null) {
            LOGGER.error("Invalid url. [url={}]", urlFromParams);

            ctx.sessionAttribute("flash", "Некорректный URL");
            ctx.sessionAttribute("flash-type", "danger");
            ctx.attribute("url", urlFromParams);
            ctx.redirect("/");
            return;
        }

        if (getUrlByName(normalizedUrl) != null) {
            LOGGER.error("Url already exists. [url={}]", urlFromParams);

            ctx.sessionAttribute("flash", "Страница уже существует");
            ctx.sessionAttribute("flash-type", "info");
            ctx.redirect("/urls");
            return;
        }

        LOGGER.info("Url added successfully. [url={}]", urlFromParams);
        Url url = new Url(normalizedUrl);
        url.save();

        ctx.sessionAttribute("flash", "Страница успешно добавлена");
        ctx.sessionAttribute("flash-type", "success");
        ctx.redirect("/urls");
    };

    public static Handler showUrl = ctx -> {
        final Long id = ctx.pathParamAsClass("id", Long.class).getOrDefault(null);

        LOGGER.info("Request url by id. [id={}]", id);

        final Url url = getUrlById(id);

        if (url == null) {
            LOGGER.error("Request url by id, not found. [id={}]", id);
            throw new NotFoundResponse();
        }

        final List<UrlCheck> urlChecks = url.getUrlChecks();

        LOGGER.error("Request url by id, found. [id={}]", id);

        ctx.attribute("url", url);
        ctx.attribute("urlChecks", urlChecks);
        ctx.render("show.html");
    };

    public static Handler checkUrl = ctx -> {
        final Long id = ctx.pathParamAsClass("id", Long.class).getOrDefault(null);
        final Url url = getUrlById(id);

        LOGGER.info("Request url verification. [url={}]", url.getName());

        try {
            LOGGER.info("Loading page by url for verification. [url={}]", url.getName());

            final HttpResponse<String> response = Unirest
                    .get(url.getName())
                    .asString();

            LOGGER.info("Parsing page. [url={}]", url.getName());

            final Integer statusCode = response.getStatus();
            final Document body = Jsoup.parse(response.getBody());
            final String title = body.title();
            final String h1 = body.selectFirst("h1") != null
                    ? Objects.requireNonNull(body.selectFirst("h1")).text()
                    : null;
            final String description = body.selectFirst("meta[name=description]") != null
                    ? Objects.requireNonNull(body.selectFirst("meta[name=description]")).attr("content")
                    : null;
            final var urlCheck = new UrlCheck(statusCode, title, h1, description, url);

            urlCheck.save();

            LOGGER.info("Url verification completed. [url={}]", url.getName());

            ctx.sessionAttribute("flash", "Страница успешно проверена");
            ctx.sessionAttribute("flash-type", "success");
        } catch (UnirestException e) {
            LOGGER.info("Url verification error. [url={}]", url.getName());

            ctx.sessionAttribute("flash", "Не удалось проверить страницу");
            ctx.sessionAttribute("flash-type", "danger");
        }

        ctx.redirect("/urls/" + id);
    };

    private static PagedList<Url> getUrlsWithChecksByPage(final int page) {
        final int rowsPerPage = 10;
        return new QUrl()
                .setFirstRow(page * rowsPerPage)
                .setMaxRows(rowsPerPage)
                .orderBy().id.asc()
                .urlChecks.fetch(QUrlCheck.alias().statusCode, QUrlCheck.alias().createdAt)
                .orderBy().urlChecks.createdAt.desc()
                .findPagedList();
    }

    private static @Nullable Url getUrlByName(@Nullable final String url) {
        return new QUrl()
                .name.equalTo(url)
                .findOne();
    }

    private static @Nullable Url getUrlById(@Nullable final Long id) {
        return new QUrl()
                .id.equalTo(id)
                .findOne();
    }

    private static @Nullable String getNormalizedUrl(@Nullable final String s) {
        try {
            LOGGER.info("Try to normalize url. [url={}]", s);

            final var url = new URL(s);
            String normalizedUrl = url.getProtocol() + "://" + url.getHost();
            final int port = url.getPort();
            if (port > 0) {
                normalizedUrl = normalizedUrl + ":" + port;
            }

            LOGGER.info("Normalized url. [url={}, normalizedUrl={}]", s, normalizedUrl);
            return normalizedUrl;
        } catch (MalformedURLException e) {
            LOGGER.error("Url normalization error. [url={}]", s);
            return null;
        }
    }
}
