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

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

import static org.tomitribe.nexus.NexusAsserts.assertType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives each state in isolation with a stubbed {@link HttpClient}, and asserts the
 * {@link NexusUnknown} transition with the jaws {@code assertType} pattern.
 */
public class NexusStateTest {

    private final AtomicInteger heads = new AtomicInteger();
    private NexusFileSystem fs;

    @BeforeEach
    public void setup() {
        final URI base = URI.create("https://nexus.example/repo/");
        fs = new NexusFileSystem(new NexusFileSystemProvider(), base, new Stub());
    }

    @Test
    public void unknownResolvesToDir() throws Exception {
        final Path path = fs.getPath("org/apache");
        assertType(path, "unresolved");

        final BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

        assertTrue(attrs.isDirectory());
        assertType(path, "NexusDir");
    }

    @Test
    public void unknownResolvesToFile() throws Exception {
        final Path path = fs.getPath("org/apache/foo.jar");
        assertType(path, "unresolved");

        assertTrue(Files.readAttributes(path, BasicFileAttributes.class).isRegularFile());
        assertType(path, "NexusFile");
        assertEquals(5, Files.size(path));
    }

    @Test
    public void unknownResolvesToMissing() {
        final Path path = fs.getPath("org/missing.txt");
        assertType(path, "unresolved");

        assertThrows(NoSuchFileException.class,
                () -> Files.readAttributes(path, BasicFileAttributes.class));
        assertType(path, "NexusMissing");
    }

    @Test
    public void resolvesAtMostOnce() throws Exception {
        final Path path = fs.getPath("org/apache/foo.jar");
        Files.readAttributes(path, BasicFileAttributes.class);
        Files.readAttributes(path, BasicFileAttributes.class);
        Files.size(path);
        assertEquals(1, heads.get(), "expected the HEAD to be cached after first resolve");
    }

    @Test
    public void directoryRejectsRead() {
        final Path dir = fs.getPath("org/apache");
        assertThrows(UnsupportedOperationException.class, () -> Files.newInputStream(dir));
    }

    @Test
    public void fileRejectsListing() {
        final Path file = fs.getPath("org/apache/foo.jar");
        assertThrows(UnsupportedOperationException.class, () -> Files.newDirectoryStream(file));
    }

    @Test
    public void offBaseIsUnrepresentable() {
        // The .. sequence tries to climb above the base; chroot normalize clamps it at
        // root, so the result is a (harmless) path *inside* the repo — never the host's
        // /etc/passwd. There is no host or scheme to redirect, so off-base can't be expressed.
        final Path escape = fs.getPath("org/apache/../../../../etc/passwd").normalize();
        assertEquals("https://nexus.example/repo/etc/passwd", escape.toUri().toString());
        assertTrue(escape.toUri().getPath().startsWith("/repo/"),
                "must stay under the base path: " + escape.toUri());
    }

    /** Classifies by path suffix: *.jar/*.pom -> file, "missing" -> 404, otherwise directory. */
    private class Stub extends HttpClient {

        Stub() {
            super(null);
        }

        @Override
        public HttpResponse head(final URI uri) {
            heads.incrementAndGet();
            final String path = uri.getPath();
            if (path.contains("missing")) return response(404, null, null);
            if (path.endsWith(".jar") || path.endsWith(".pom")) {
                return response(200, "application/java-archive", null);
            }
            return response(200, "text/html", null);
        }

        @Override
        public HttpResponse get(final URI uri) {
            final String path = uri.getPath();
            if (path.contains("missing")) return response(404, null, null);
            if (path.endsWith(".jar") || path.endsWith(".pom")) {
                return response(200, "application/java-archive", "hello".getBytes(StandardCharsets.UTF_8));
            }
            return response(200, "text/html", "<html/>".getBytes(StandardCharsets.UTF_8));
        }

        private HttpResponse response(final int status, final String contentType, final byte[] body) {
            final BasicHttpResponse response =
                    new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "reason"));
            if (contentType != null) response.setHeader("Content-Type", contentType);
            if (body != null) {
                response.setHeader("Content-Length", String.valueOf(body.length));
                response.setEntity(new ByteArrayEntity(body));
            } else if (status == 200) {
                response.setHeader("Content-Length", "5");
            }
            return response;
        }
    }
}
