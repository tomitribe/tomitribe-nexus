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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every {@link Path} method on the unresolved state, exercised through the public API against a
 * live server. The unresolved path is what {@code root.resolve("…")} yields before any I/O — the
 * resting state of every path a caller names. Structural algebra must never touch the network.
 */
public class NexusUnknownUsageTest {

    private static final String VERSION = "org/apache/tomee/apache-tomee/9.0.1-TT.2";

    private static Foo foo;
    private static HttpServer server;
    private static Path root;

    private Path p; // fresh, unresolved, per test

    @BeforeAll
    public static void up() throws Exception {
        foo = new Foo();
        server = HttpServer.start(foo);
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
    public void freshPath() {
        p = root.resolve(VERSION);
        assertType(p, "unresolved");
    }

    @Test
    public void getFileSystem() {
        assertEquals(root.getFileSystem(), p.getFileSystem());
    }

    @Test
    public void isAbsolute() {
        assertTrue(p.isAbsolute());
    }

    @Test
    public void getRoot() {
        assertEquals("/", p.getRoot().toString());
    }

    @Test
    public void getFileName() {
        assertEquals("9.0.1-TT.2", p.getFileName().toString());
        assertType(p.getFileName(), "unresolved");
    }

    @Test
    public void getParent() {
        assertEquals("/org/apache/tomee/apache-tomee", p.getParent().toString());
        assertType(p.getParent(), "NexusDir");
    }

    @Test
    public void getNameCountAndGetName() {
        assertEquals(5, p.getNameCount());
        assertEquals("org", p.getName(0).toString());
        assertEquals("9.0.1-TT.2", p.getName(4).toString());
    }

    @Test
    public void subpath() {
        assertEquals("org/apache/tomee/apache-tomee", p.subpath(0, 4).toString());
        assertType(p.subpath(0, 4), "NexusDir");
    }

    @Test
    public void startsWith() {
        assertTrue(p.startsWith(root.resolve("org/apache")));
        assertTrue(p.startsWith("org/apache"));
        assertFalse(p.startsWith("apache"));
    }

    @Test
    public void endsWith() {
        assertTrue(p.endsWith("9.0.1-TT.2"));
        assertTrue(p.endsWith("apache-tomee/9.0.1-TT.2"));
        assertFalse(p.endsWith("apache-tomee"));
    }

    @Test
    public void normalizePeeksWithoutResolving() {
        final int before = foo.heads();
        assertType(p.normalize(), "unresolved");
        assertEquals(before, foo.heads(), "normalize() must not touch the network");
    }

    @Test
    public void resolveAndResolveSibling() {
        assertEquals("/org/apache/tomee/apache-tomee/9.0.1-TT.2/x.jar", p.resolve("x.jar").toString());
        assertType(p.resolve("x.jar"), "unresolved");
        assertEquals("/org/apache/tomee/apache-tomee/9.0.1-TT.1", p.resolveSibling("9.0.1-TT.1").toString());
    }

    @Test
    public void relativize() {
        assertEquals("a/b", p.relativize(root.resolve(VERSION + "/a/b")).toString());
    }

    @Test
    public void toUri() {
        assertEquals("/content/repositories/releases-tomitribe/" + VERSION, p.toUri().getPath());
    }

    @Test
    public void toAbsolutePath() {
        assertEquals(p, p.toAbsolutePath());
    }

    @Test
    public void toFileIsUnsupported() {
        assertThrows(UnsupportedOperationException.class, p::toFile);
    }

    @Test
    public void registerIsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> p.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void iterator() {
        final List<String> names = new ArrayList<>();
        p.iterator().forEachRemaining(part -> names.add(part.toString()));
        assertEquals(List.of("org", "apache", "tomee", "apache-tomee", "9.0.1-TT.2"), names);
    }

    @Test
    public void compareToAndEqualsAndHashCode() {
        assertTrue(p.compareTo(root.resolve("org/apache/tomee/apache-tomee/9.0.1-TT.1")) > 0);
        final Path same = root.resolve(VERSION);
        assertEquals(p, same);
        assertEquals(p.hashCode(), same.hashCode());
    }

    @Test
    public void testToString() {
        assertEquals("/" + VERSION, p.toString());
    }

    @Test
    public void toRealPathForcesResolution() throws Exception {
        final Path real = p.toRealPath();
        assertType(real, "NexusDir");
    }

    @Test
    public void resolvesLazilyAndAtMostOnce() throws Exception {
        final int before = foo.heads();
        assertTrue(before == foo.heads()); // naming the path did nothing

        assertTrue(Files.isDirectory(p));   // first touch resolves
        assertType(p, "NexusDir");
        final int afterFirst = foo.heads();
        assertTrue(afterFirst > before, "resolution should HEAD");

        Files.isDirectory(p);
        Files.readAttributes(p, BasicFileAttributes.class);
        assertEquals(afterFirst, foo.heads(), "resolution must be cached — no further HEADs");
    }
}
