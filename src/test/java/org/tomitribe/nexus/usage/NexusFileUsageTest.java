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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tomitribe.nexus.Foo;
import org.tomitribe.nexus.HttpServer;
import org.tomitribe.nexus.Nexus;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every {@link Path} method on a resolved file, plus the file behaviors (read, copy, size), all
 * through the public API. When you download it to disk you get a file — so reading is legal and
 * listing is not.
 */
public class NexusFileUsageTest {

    private static final String VERSION = "org/apache/tomee/apache-tomee/9.0.1-TT.2";
    private static final String POM = VERSION + "/apache-tomee-9.0.1-TT.2.pom";

    private static HttpServer server;
    private static Path root;

    private Path file; // resolved NexusFile, per test

    @BeforeAll
    public static void up() throws Exception {
        server = HttpServer.start(new Foo());
        root = Nexus.builder()
                .baseUri(server.getUri().resolve("/content/repositories/releases-tomitribe/"))
                .credentials("snoopy", "woodstock")
                .build();
    }

    @AfterAll
    public static void down() throws Exception {
        server.close();
    }

    @BeforeEach
    public void resolveFile() throws Exception {
        file = root.resolve(POM).toRealPath();
        assertType(file, "NexusFile");
    }

    // ---- structural -----------------------------------------------------------

    @Test
    public void getFileName() {
        assertEquals("apache-tomee-9.0.1-TT.2.pom", file.getFileName().toString());
        assertType(file.getFileName(), "NexusFile"); // terminal carries the file kind
    }

    @Test
    public void getParent() {
        assertEquals("/" + VERSION, file.getParent().toString());
        assertType(file.getParent(), "NexusDir"); // a parent is always a directory
    }

    @Test
    public void getNameCountNameSubpath() {
        assertEquals(6, file.getNameCount());
        assertEquals("apache-tomee-9.0.1-TT.2.pom", file.getName(5).toString());
        assertType(file.getName(5), "NexusFile");
        assertType(file.getName(0), "NexusDir");
        assertType(file.subpath(0, 5), "NexusDir");
        assertType(file.subpath(0, 6), "NexusFile");
    }

    @Test
    public void startsWithEndsWith() {
        assertTrue(file.startsWith("org/apache/tomee"));
        assertTrue(file.endsWith("apache-tomee-9.0.1-TT.2.pom"));
        assertTrue(file.endsWith("9.0.1-TT.2/apache-tomee-9.0.1-TT.2.pom"));
    }

    @Test
    public void normalize() {
        assertType(file.normalize(), "NexusFile");
        assertEquals(file, file.normalize());
    }

    @Test
    public void resolveAndRelativize() {
        assertType(file.resolve("child"), "unresolved");
        assertEquals("x", root.resolve(VERSION).relativize(root.resolve(VERSION + "/x")).toString());
    }

    @Test
    public void toUriHasNoTrailingSlash() {
        assertEquals("/content/repositories/releases-tomitribe/" + POM, file.toUri().getPath());
    }

    @Test
    public void toAbsolutePathAndToRealPath() throws Exception {
        assertEquals(file, file.toAbsolutePath());
        assertEquals(file, file.toRealPath());
    }

    @Test
    public void toFileAndRegisterUnsupported() {
        assertThrows(UnsupportedOperationException.class, file::toFile);
        assertThrows(UnsupportedOperationException.class,
                () -> file.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void compareEqualsToString() {
        assertEquals(file, root.resolve(POM)); // path-only identity
        assertEquals("/" + POM, file.toString());
        assertTrue(file.compareTo(root.resolve(VERSION + "/a.pom")) > 0);
    }

    // ---- file behavior --------------------------------------------------------

    @Test
    public void isRegularFileAndAttributes() throws Exception {
        assertTrue(Files.isRegularFile(file));
        final BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        assertTrue(attrs.isRegularFile());
        assertFalse(attrs.isDirectory());
    }

    @Test
    public void sizeIsAvailable() throws Exception {
        assertTrue(Files.size(file) > 0);
    }

    @Test
    public void readContent() throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            final String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains("<artifactId>apache-tomee</artifactId>"), content);
        }
    }

    @Test
    public void copyToLocalDisk() throws Exception {
        final Path local = Files.createTempFile("apache-tomee", ".pom");
        Files.copy(file, local, REPLACE_EXISTING);
        assertTrue(Files.readString(local).contains("<version>9.0.1-TT.2</version>"));
    }

    @Test
    public void listingAFileIsRejected() {
        assertThrows(UnsupportedOperationException.class, () -> Files.newDirectoryStream(file));
    }
}
