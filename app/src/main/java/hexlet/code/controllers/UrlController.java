package hexlet.code.controllers;

import hexlet.code.domain.Url;
import hexlet.code.domain.query.QUrl;
import io.ebean.PagedList;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.IntStream;

public final class UrlController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UrlController.class);

    public static Handler listUrls = ctx -> {
        LOGGER.info("Request urls list.");

        final int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(1) - 1;
        final PagedList<Url> pagedUrls = getUrlsByPage(page);
        final int lastPage = pagedUrls.getTotalPageCount() + 1;
        final int currentPage = pagedUrls.getPageIndex() + 1;
        final List<Integer> pages = IntStream
                .range(1, lastPage)
                .boxed()
                .toList();

        ctx.attribute("urls", pagedUrls.getList());
        ctx.attribute("pages", pages);
        ctx.attribute("currentPage", currentPage);
        ctx.render("urls.html");
    };


    public static Handler createUrl = ctx -> {
        final String urlFromParams = ctx.formParam("url");
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
            LOGGER.error("Page already exists. [url={}]", normalizedUrl);
            ctx.sessionAttribute("flash", "Страница уже существует");
            ctx.sessionAttribute("flash-type", "danger");
            ctx.redirect("/urls");
            return;
        }

        LOGGER.info("Page added successfully. [url={}]", normalizedUrl);
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
            throw new NotFoundResponse();
        }

        ctx.attribute("url", url);
        ctx.render("show.html");
    };

    private static PagedList<Url> getUrlsByPage(final int page) {
        final int rowsPerPage = 10;
        return new QUrl()
                .setFirstRow(page * rowsPerPage)
                .setMaxRows(rowsPerPage)
                .orderBy().id.asc()
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
