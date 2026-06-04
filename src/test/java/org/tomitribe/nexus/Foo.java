/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tomitribe.nexus;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves the HTML directory-listing fixtures, requiring Basic auth (snoopy/woodstock).
 * A plain {@link HttpServlet}, no JAX-RS, faithfully emulating the two server styles we support:
 *
 * <ul>
 *   <li><b>Nexus 2</b> (default section) — HEAD responses reproduce a real
 *       {@code Server: Nexus/2.14.19-01} repository exactly: a <em>directory</em> HEAD is
 *       {@code 200} with <em>no</em> {@code Content-Type} and {@code Content-Length: 0}; a
 *       <em>file</em> HEAD carries a content type, an {@code ETag}, and a non-zero length. No
 *       redirect — Nexus 2 serves the directory at the no-slash URL directly.</li>
 *   <li><b>Central</b> ({@code "central"} section) — a no-slash directory 301-redirects to the
 *       slash form, which is served as {@code text/html}.</li>
 * </ul>
 *
 * <p>The Nexus 2 header sets below mirror the live responses captured from
 * {@code repository.tomitribe.com} — 7 headers for a directory, 9 for a file.
 *
 * <p>Counts HEAD and GET requests so a black-box test can observe real HTTP traffic.
 */
public class Foo extends HttpServlet {

    private static final String NEXUS2_SERVER = "Nexus/2.14.19-01";
    private static final String LAST_MODIFIED = "Wed, 27 May 2026 08:49:05 GMT";
    private static final String ETAG = "\"{SHA1{6fcf613bb794d8f4e4d37cdf2bb0b83b945014cb}}\"";

    private final AtomicInteger heads = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final String section;
    private final boolean nexus2;

    public Foo() {
        this("fixtures");
    }

    public Foo(final String section) {
        this.section = section;
        this.nexus2 = !"central".equals(section);
    }

    public int heads() {
        return heads.get();
    }

    public int gets() {
        return gets.get();
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        if ("HEAD".equals(req.getMethod())) {
            heads.incrementAndGet();
        } else if ("GET".equals(req.getMethod())) {
            gets.incrementAndGet();
        }
        super.service(req, resp);
    }

    @Override
    protected void doHead(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        if (!authorized(req, resp)) return;

        final String name = name(req);
        final boolean directory = name.endsWith("_") || resource(name) == null && resource(name + "_") != null;
        final boolean file = !name.endsWith("_") && resource(name) != null;

        if (!nexus2) {                                  // Central-style
            if (file) {
                resp.setStatus(200);
                resp.setContentType("application/octet-stream");
                resp.setContentLengthLong(body(req, resource(name)).length);
            } else if (name.endsWith("_")) {            // already the slash form
                resp.setStatus(200);
                resp.setContentType("text/html");
            } else if (directory) {                     // no-slash form → redirect to slash
                resp.setStatus(301);
                resp.setHeader("Location", req.getRequestURL().toString() + "/");
            } else {
                resp.setStatus(404);
            }
            return;
        }

        // Nexus 2 — exactly as the live server answers a HEAD.
        if (file) {                                     // 9 headers, with a content type and length
            resp.setStatus(200);
            nexus2Headers(resp);
            resp.setHeader("ETag", ETAG);
            resp.setContentType(contentType(name));
            resp.setContentLengthLong(body(req, resource(name)).length);
        } else if (directory) {                         // 7 headers, NO content type, length 0
            resp.setStatus(200);
            nexus2Headers(resp);
            resp.setContentLengthLong(0);
        } else {
            resp.setStatus(404);
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        if (!authorized(req, resp)) return;

        final String name = name(req);
        final URL resource = resource(name);
        if (resource == null) {
            resp.setStatus(404);
            return;
        }
        if (nexus2) nexus2Headers(resp);
        resp.setContentType(name.endsWith("_") ? "text/html" : contentType(name));
        resp.getOutputStream().write(body(req, resource));
    }

    /** The decoration every Nexus 2 response carries (Server + the security/range headers + date). */
    private void nexus2Headers(final HttpServletResponse resp) {
        resp.setHeader("Server", NEXUS2_SERVER);
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Accept-Ranges", "bytes");
        resp.setHeader("Last-Modified", LAST_MODIFIED);
    }

    private static String contentType(final String name) {
        if (name.endsWith(".xml") || name.endsWith(".pom")) return "application/xml";
        if (name.endsWith(".jar")) return "application/java-archive";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".gz")) return "application/gzip";
        return "application/octet-stream";
    }

    private String name(final HttpServletRequest req) {
        return req.getRequestURI().replaceAll(".*/apache-tomee", "apache-tomee").replace("/", "_");
    }

    private URL resource(final String name) {
        return getClass().getClassLoader().getResource(section + "/" + name);
    }

    private byte[] body(final HttpServletRequest req, final URL resource) throws IOException {
        final URI uri = URI.create(req.getRequestURL().toString());
        final String raw;
        try (var in = resource.openStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return raw.replace("https://nexus.example",
                String.format("http://%s:%s", uri.getHost(), uri.getPort())).getBytes(StandardCharsets.UTF_8);
    }

    private boolean authorized(final HttpServletRequest req, final HttpServletResponse resp) {
        final String authorization = req.getHeader("authorization");
        if (authorization == null) {
            resp.setStatus(480);
            return false;
        }
        if (!authorization.startsWith("Basic ")) {
            resp.setStatus(481);
            return false;
        }
        final byte[] decoded = Base64.getDecoder().decode(authorization.replace("Basic ", "").trim());
        final String[] parts = new String(decoded, StandardCharsets.UTF_8).split(":");
        if (parts.length != 2) {
            resp.setStatus(482);
            return false;
        }
        if (!parts[0].equals("snoopy")) {
            resp.setStatus(483);
            return false;
        }
        if (!parts[1].equals("woodstock")) {
            resp.setStatus(484);
            return false;
        }
        return true;
    }
}
