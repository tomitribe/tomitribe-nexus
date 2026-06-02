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

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every {@link Path} method on a resolved directory, plus the directory behaviors, all through
 * the public API. The handle is obtained the way a caller would — resolve a path and let
 * {@code toRealPath()} settle it — never by constructing the type.
 */
public class NexusDirUsageTest {

    private static final String VERSION = "org/apache/tomee/apache-tomee/9.0.1-TT.2";

    private static HttpServer server;
    private static Path root;

    private Path dir; // resolved NexusDir, per test

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
    public void resolveDir() throws Exception {
        dir = root.resolve(VERSION).toRealPath();
        assertType(dir, "NexusDir");
    }

    // ---- structural -----------------------------------------------------------

    @Test
    public void isAbsoluteAndRootAndFileSystem() {
        assertTrue(dir.isAbsolute());
        assertEquals("/", dir.getRoot().toString());
        assertEquals(root.getFileSystem(), dir.getFileSystem());
    }

    @Test
    public void getFileName() {
        assertEquals("9.0.1-TT.2", dir.getFileName().toString());
        assertType(dir.getFileName(), "NexusDir");
    }

    @Test
    public void getParent() {
        assertEquals("/org/apache/tomee/apache-tomee", dir.getParent().toString());
        assertType(dir.getParent(), "NexusDir");
    }

    @Test
    public void getNameCountNameSubpath() {
        assertEquals(5, dir.getNameCount());
        assertEquals("apache-tomee", dir.getName(3).toString());
        assertEquals("tomee/apache-tomee", dir.subpath(2, 4).toString());
        assertType(dir.subpath(2, 4), "NexusDir");
    }

    @Test
    public void startsWithEndsWith() {
        assertTrue(dir.startsWith("org/apache/tomee"));
        assertTrue(dir.endsWith("apache-tomee/9.0.1-TT.2"));
        assertFalse(dir.endsWith("apache-tomee"));
    }

    @Test
    public void normalize() {
        assertType(dir.normalize(), "NexusDir");
        assertEquals(dir, dir.normalize());
    }

    @Test
    public void resolveAndRelativizeAndSibling() {
        assertEquals("/" + VERSION + "/a.jar", dir.resolve("a.jar").toString());
        assertEquals("a.jar", dir.relativize(root.resolve(VERSION + "/a.jar")).toString());
        assertEquals("/org/apache/tomee/apache-tomee/9.0.1-TT.1", dir.resolveSibling("9.0.1-TT.1").toString());
    }

    @Test
    public void toUriHasTrailingSlash() {
        assertEquals("/content/repositories/releases-tomitribe/" + VERSION + "/", dir.toUri().getPath());
    }

    @Test
    public void toAbsolutePathAndToRealPath() throws Exception {
        assertEquals(dir, dir.toAbsolutePath());
        assertEquals(dir, dir.toRealPath());
    }

    @Test
    public void toFileAndRegisterUnsupported() {
        assertThrows(UnsupportedOperationException.class, dir::toFile);
        assertThrows(UnsupportedOperationException.class,
                () -> dir.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void iteratorCompareEqualsToString() {
        final List<String> names = new ArrayList<>();
        dir.iterator().forEachRemaining(part -> names.add(part.toString()));
        assertEquals(5, names.size());
        assertEquals(dir, root.resolve(VERSION)); // path-only identity, regardless of state
        assertEquals("/" + VERSION, dir.toString());
    }

    // ---- directory behavior ---------------------------------------------------

    @Test
    public void isDirectoryAndAttributes() throws Exception {
        assertTrue(Files.isDirectory(dir));
        final BasicFileAttributes attrs = Files.readAttributes(dir, BasicFileAttributes.class);
        assertTrue(attrs.isDirectory());
        assertFalse(attrs.isRegularFile());
    }

    @Test
    public void newDirectoryStreamListsChildren() throws Exception {
        final List<String> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            stream.forEach(child -> children.add(child.getFileName().toString()));
        }
        assertTrue(children.contains("apache-tomee-9.0.1-TT.2.pom"), children.toString());
    }

    @Test
    public void walkFindsArtifacts() throws Exception {
        try (Stream<Path> walk = Files.walk(dir)) {
            final List<String> files = walk.filter(Files::isRegularFile).map(Path::toString).sorted().toList();
            assertTrue(files.contains("/" + VERSION + "/apache-tomee-9.0.1-TT.2.pom"), files.toString());
        }
    }

    @Test
    public void readingADirectoryIsRejected() {
        assertThrows(UnsupportedOperationException.class, () -> Files.newInputStream(dir));
    }
}
