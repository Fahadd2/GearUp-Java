package com.gearup.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves the frontend (HTML, CSS and JavaScript) from inside the JAR, so one process serves
 * both the pages and the API on the same port (Factor VII).
 *
 * <p>Only flat file names such as {@code staff.html} are allowed. A path with a slash, "..",
 * or an unknown extension is refused, so a request can never reach other files in the JAR.
 */
public final class StaticFiles {

    private static final Pattern FILE_NAME = Pattern.compile("/([A-Za-z0-9_-]+\\.(html|css|js))");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8");

    private final String folder;

    /** @param folder the folder inside the JAR that holds the files, e.g. {@code "/static"} */
    public StaticFiles(String folder) {
        this.folder = folder;
    }

    /** The file for a request path, or empty if there is no such file. {@code "/"} means {@code index.html}. */
    public Optional<Response> find(String requestPath) {
        String path = requestPath.equals("/") ? "/index.html" : requestPath;
        Matcher matcher = FILE_NAME.matcher(path);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try (InputStream in = StaticFiles.class.getResourceAsStream(folder + path)) {
            if (in == null) {
                return Optional.empty();
            }
            String contentType = CONTENT_TYPES.get(matcher.group(2));
            return Optional.of(new Response(200, contentType, in.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read static file " + path, e);
        }
    }
}
