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
 * A plain {@link HttpServlet}, no JAX-RS. The request path is mapped to a classpath
 * fixture, and {@code https://nexus.example} in the listing is rewritten to the live
 * test host. {@code doHead} falls through to {@code doGet} via the servlet contract.
 *
 * <p>Counts HEAD and GET requests so a black-box test can observe real HTTP traffic —
 * e.g. that resolution HEADs at most once and that walking a tree never HEADs a file.
 */
public class Foo extends HttpServlet {

    private final AtomicInteger heads = new AtomicInteger();
    private final AtomicInteger gets = new AtomicInteger();
    private final String section;

    public Foo() {
        this("fixtures");
    }

    public Foo(final String section) {
        this.section = section;
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
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final String authorization = req.getHeader("authorization");
        if (authorization == null) {
            resp.setStatus(480);
            return;
        }
        if (!authorization.startsWith("Basic ")) {
            resp.setStatus(481);
            return;
        }

        final byte[] decoded = Base64.getDecoder().decode(authorization.replace("Basic ", "").trim());
        final String[] parts = new String(decoded, StandardCharsets.UTF_8).split(":");
        if (parts.length != 2) {
            resp.setStatus(482);
            return;
        }
        if (!parts[0].equals("snoopy")) {
            resp.setStatus(483);
            return;
        }
        if (!parts[1].equals("woodstock")) {
            resp.setStatus(484);
            return;
        }

        final String name = req.getRequestURI().replaceAll(".*/apache-tomee", "apache-tomee").replace("/", "_");
        URL resource = getClass().getClassLoader().getResource(section + "/" + name);
        if (resource == null) {
            // Like a real Nexus: a directory requested without a trailing slash 301-redirects to
            // the slash form (our dir fixtures are stored with a trailing underscore). HttpClient
            // follows it on HEAD and GET, so resolution lands on the listing.
            if (getClass().getClassLoader().getResource(section + "/" + name + "_") != null) {
                resp.setStatus(301);
                resp.setHeader("Location", req.getRequestURL().toString() + "/");
                return;
            }
            // Otherwise it genuinely doesn't exist.
            resp.setStatus(404);
            return;
        }

        final URI uri = URI.create(req.getRequestURL().toString());
        final String raw;
        try (var in = resource.openStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String content = raw.replace("https://nexus.example",
                String.format("http://%s:%s", uri.getHost(), uri.getPort()));

        // Directory listings (fixtures stored with a trailing underscore) are HTML; everything
        // else is a file with a non-HTML type — that's how resolution tells dir from file.
        resp.setContentType(name.endsWith("_") ? "text/html" : "application/octet-stream");
        resp.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }
}
