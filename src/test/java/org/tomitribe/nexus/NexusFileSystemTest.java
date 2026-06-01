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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the lattice against the real HTML fixtures: a {@code Files.walk} crawl
 * through {@link NexusDir}/{@link NexusFile} states, and a {@code Files.copy} pulling a
 * file to local disk — vanilla NIO, no Nexus-specific call-site vocabulary.
 */
public class NexusFileSystemTest {

    private static HttpServer httpServer;
    private static Path root;

    @BeforeAll
    public static void setup() throws Exception {
        httpServer = HttpServer.start(new Foo());
        final URI base = httpServer.getUri().resolve("/content/repositories/releases-tomitribe/");
        root = Nexus.root(base, "snoopy", "woodstock");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        httpServer.close();
    }

    @Test
    public void walk() throws Exception {
        final NexusDir start = ((NexusDir) root).dir("org/apache/tomee/apache-tomee");

        final List<String> files;
        try (final Stream<Path> walk = Files.walk(start)) {
            files = walk.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }

        assertTrue(files.contains("/org/apache/tomee/apache-tomee/maven-metadata.xml"), files.toString());
        assertTrue(files.contains("/org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom"), files.toString());
        // The fixtures carry well over a hundred artifacts across eight versions.
        assertTrue(files.size() > 150, "found only " + files.size());
    }

    @Test
    public void copyToLocalDisk() throws Exception {
        final NexusFile pom = ((NexusDir) root)
                .file("org/apache/tomee/apache-tomee/9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom");

        final Path local = Files.createTempFile("apache-tomee-9.0.1-TT.2", ".pom");
        Files.copy(pom, local, REPLACE_EXISTING);

        final String content = Files.readString(local);
        assertTrue(content.contains("<artifactId>apache-tomee</artifactId>"), content);
        assertTrue(content.contains("<version>9.0.1-TT.2</version>"), content);
    }

    @Test
    public void filterAndCount() throws Exception {
        final NexusDir start = ((NexusDir) root).dir("org/apache/tomee/apache-tomee");

        final long zips;
        try (final Stream<Path> walk = Files.walk(start)) {
            zips = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .count();
        }

        assertTrue(zips > 0, "expected to find .zip artifacts");
    }
}
