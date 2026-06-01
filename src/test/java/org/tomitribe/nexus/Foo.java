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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Serves the HTML directory-listing fixtures, requiring Basic auth (snoopy/woodstock).
 * A plain {@link HttpServlet}, no JAX-RS. The request path is mapped to a classpath
 * fixture, and {@code https://nexus.example} in the listing is rewritten to the live
 * test host. {@code doHead} falls through to {@code doGet} via the servlet contract.
 */
public class Foo extends HttpServlet {

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
        final URL resource = getClass().getClassLoader().getResource("fixtures/" + name);
        if (resource == null) {
            resp.setStatus(485);
            return;
        }

        final URI uri = URI.create(req.getRequestURL().toString());
        final String raw;
        try (var in = resource.openStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String content = raw.replace("https://nexus.example",
                String.format("http://%s:%s", uri.getHost(), uri.getPort()));

        resp.setContentType("text/html");
        resp.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }
}
