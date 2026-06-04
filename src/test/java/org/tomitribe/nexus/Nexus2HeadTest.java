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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the test fixture's HEAD responses to the exact shape a live {@code Server: Nexus/2.x}
 * repository returns — captured from {@code repository.tomitribe.com}. This is what the
 * directory/file resolution heuristic keys off, so if the fixture ever drifts from the real
 * server this test catches it. Issued with a raw HTTP client so it observes the wire directly,
 * not through our path lattice.
 *
 * <ul>
 *   <li>Directory: {@code 200}, no {@code Content-Type}, {@code Content-Length: 0}.</li>
 *   <li>File: {@code 200}, a {@code Content-Type}, an {@code ETag}, and a non-zero length.</li>
 * </ul>
 */
public class Nexus2HeadTest {

    private static final String AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("snoopy:woodstock".getBytes(StandardCharsets.UTF_8));

    private static HttpServer server;
    private static URI base;
    private static java.net.http.HttpClient http;

    @BeforeAll
    public static void up() throws Exception {
        server = HttpServer.start(new Foo()); // default section = Nexus 2
        base = server.getUri().resolve("/content/repositories/releases-tomitribe/");
        http = java.net.http.HttpClient.newHttpClient();
    }

    @AfterAll
    public static void down() throws Exception {
        server.close();
    }

    @Test
    public void directoryHead() throws Exception {
        final HttpResponse<Void> response = head("org/apache/tomee/apache-tomee");

        assertEquals(200, response.statusCode());
        assertTrue(server(response).startsWith("Nexus/2"), server(response));
        // The tell: a Nexus 2 directory HEAD has NO content type and a zero length.
        assertFalse(response.headers().firstValue("Content-Type").isPresent(), "directory must have no Content-Type");
        assertEquals("0", response.headers().firstValue("Content-Length").orElseThrow());
        // ...plus the rest of the live response's decoration.
        assertEquals("SAMEORIGIN", response.headers().firstValue("X-Frame-Options").orElseThrow());
        assertTrue(response.headers().firstValue("Last-Modified").isPresent());
    }

    @Test
    public void fileHead() throws Exception {
        final HttpResponse<Void> response = head("org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom");

        assertEquals(200, response.statusCode());
        assertTrue(server(response).startsWith("Nexus/2"), server(response));
        // A file carries a content type, an ETag, and a non-zero length.
        assertEquals("application/xml", response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("ETag").isPresent());
        assertTrue(Long.parseLong(response.headers().firstValue("Content-Length").orElseThrow()) > 0);
    }

    private static HttpResponse<Void> head(final String path) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .header("Authorization", AUTH)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static String server(final HttpResponse<Void> response) {
        return response.headers().firstValue("Server").orElse("");
    }
}
