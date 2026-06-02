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
package org.tomitribe.nexus.usage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tomitribe.nexus.Foo;
import org.tomitribe.nexus.HttpServer;
import org.tomitribe.nexus.Nexus;
import org.tomitribe.util.IO;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Black box. This package can see only the public API of {@code org.tomitribe.nexus} — the
 * state types, {@code NexusFileSystem}, and the constructors are package-private and won't
 * compile here. So every path comes from {@link Nexus#builder()} and is exercised purely
 * through {@code java.nio.file.Files}/{@link Path}. This is real usage; nothing privileged.
 */
public class NexusCrawlTest {

    private static HttpServer server;
    private static Path root;

    @BeforeAll
    public static void setup() throws Exception {
        server = HttpServer.start(new Foo());
        root = Nexus.builder()
                .baseUri(server.getUri().resolve("/content/repositories/releases-tomitribe/"))
                .credentials("snoopy", "woodstock")
                .build();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        server.close();
    }

    @Test
    public void walkCrawlsTheTree() throws Exception {
        final Path version = root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.2");
        try (final Stream<Path> walk = Files.walk(version)) {

            final String actual = walk.map(Path::toString)
                    .reduce((s, s2) -> s + "\n" + s2)
                    .orElse("");

            assertEquals("/org/apache/tomee/apache-tomee/9.0.1-TT.2\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-javadoc.jar\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-javadoc.jar.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-javadoc.jar.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-javadoc.jar.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.tar.gz\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.tar.gz.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.tar.gz.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.tar.gz.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.zip\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.zip.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.zip.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-microprofile.zip.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.tar.gz\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.tar.gz.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.tar.gz.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.tar.gz.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.zip\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.zip.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.zip.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plume.zip.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.tar.gz\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.tar.gz.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.tar.gz.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.tar.gz.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.zip\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.zip.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.zip.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-plus.zip.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-sources.jar\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-sources.jar.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-sources.jar.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-sources.jar.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.tar.gz\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.tar.gz.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.tar.gz.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.tar.gz.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.zip\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.zip.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.zip.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2-webprofile.zip.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.jar\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.jar.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.jar.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.jar.sha1\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom.asc\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom.md5\n" +
                    "/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom.sha1", actual);
        }
    }

    @Test
    public void basicAttributes() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("nexus2-list.txt");
        final String expected = IO.slurp(resource);

        final Path version = root.resolve("org/apache/tomee/apache-tomee/");

        try (var stream = Files.walk(version)) {

            final String result = stream
                    .map(path -> {
                        try {
                            final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                            final long size = attributes.size();
                            final Instant modified = attributes.lastModifiedTime().toInstant();
                            return String.format("%12d  [%s]  %s", size, modified, path);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining(System.lineSeparator()));

            assertEquals(expected, result);
        }
    }

    @Test
    public void copyToLocalDisk() throws Exception {
        final Path pom = root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom");
        final Path local = Files.createTempFile("apache-tomee-9.0.1-TT.2", ".pom");
        Files.copy(pom, local, REPLACE_EXISTING);
        assertTrue(Files.readString(local).contains("<artifactId>apache-tomee</artifactId>"));
    }

    @Test
    public void resolveThenLazilyResolvesToDirectory() throws Exception {
        final Path version = root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.2");
        assertType(version, "unresolved");        // no request yet
        assertTrue(Files.isDirectory(version));    // triggers the resolve (HEAD, follows the 301)
        assertType(version, "NexusDir");           // settled — still observed without a request
    }

    @Test
    public void resolveThenLazilyResolvesToFile() throws Exception {
        final Path pom = root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom");
        assertType(pom, "unresolved");
        assertTrue(Files.isRegularFile(pom));
        assertType(pom, "NexusFile");
    }

    @Test
    public void readingADirectoryFails() {
        final Path dir = root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.2");
        assertThrows(UnsupportedOperationException.class, () -> Files.newInputStream(dir));
    }

    @Test
    public void missingArtifactIsNoSuchFile() {
        final Path missing = root.resolve("org/apache/tomee/apache-tomee/99.0.0/nope.jar");
        assertThrows(NoSuchFileException.class, () -> Files.newInputStream(missing));
        assertType(missing, "NexusMissing");
    }
}
