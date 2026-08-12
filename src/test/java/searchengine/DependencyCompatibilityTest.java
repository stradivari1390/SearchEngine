package searchengine;

import com.sun.net.httpserver.HttpServer;
import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Driver;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCompatibilityTest {

    @Test
    void parsesSearchContentAndLinksWithJsoup() {
        Document document = Jsoup.parse(
                "<html><head><title>Search &amp; Index</title></head>"
                        + "<body><main>Search <strong>result</strong></main>"
                        + "<a href=\"/docs?q=java#top\">Docs</a></body></html>");

        Element link = document.selectFirst("a[href]");

        assertNotNull(link);
        assertEquals("Search & Index", document.title());
        assertEquals("Search result", document.selectFirst("main").text());
        assertEquals("/docs?q=java#top", link.attr("href"));
    }

    @Test
    void followsRedirectAndParsesHttpResponseWithJsoup() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/page");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/page", exchange -> {
            byte[] body = "<html><head><title>Fetched</title></head><body>Content</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect";
            Connection.Response response = Jsoup.connect(url).execute();

            assertEquals(200, response.statusCode());
            assertEquals("Fetched", response.parse().title());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void loadsPostgresJdbcDriver() throws ClassNotFoundException {
        Class<?> driverClass = Class.forName("org.postgresql.Driver");

        assertTrue(Driver.class.isAssignableFrom(driverClass));
    }
}
