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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every {@link Path} method on a path that resolved to a 404. Behavioral access fails with
 * {@link NoSuchFileException}; structural algebra still works (a missing path has a perfectly
 * good name and parent). Obtained the real way: resolve a path that isn't there.
 */
public class NexusMissingUsageTest {

    private static final String MISSING = "org/apache/tomee/apache-tomee/99.0.0/nope.jar";

    private static HttpServer server;
    private static Path root;

    private Path missing; // concrete NexusMissing, per test

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
    public void resolveMissing() {
        final Path probe = root.resolve(MISSING);
        try {
            probe.toRealPath();
            fail("expected NoSuchFileException for a 404");
        } catch (final Exception expected) {
            assertTrue(expected instanceof NoSuchFileException, "got " + expected);
        }
        assertType(probe, "NexusMissing");
        missing = probe.normalize(); // the concrete missing state
        assertType(missing, "NexusMissing");
    }

    // ---- structural still works ----------------------------------------------

    @Test
    public void getFileName() {
        assertEquals("nope.jar", missing.getFileName().toString());
        assertType(missing.getFileName(), "unresolved"); // a derived path's kind is unknown again
    }

    @Test
    public void getParent() {
        assertEquals("/org/apache/tomee/apache-tomee/99.0.0", missing.getParent().toString());
        assertType(missing.getParent(), "NexusDir");
    }

    @Test
    public void getNameCountNameSubpath() {
        assertEquals(6, missing.getNameCount());
        assertEquals("nope.jar", missing.getName(5).toString());
        assertEquals("99.0.0/nope.jar", missing.subpath(4, 6).toString());
    }

    @Test
    public void startsWithEndsWith() {
        assertTrue(missing.startsWith("org/apache"));
        assertTrue(missing.endsWith("99.0.0/nope.jar"));
    }

    @Test
    public void normalizeKeepsMissing() {
        assertType(missing.normalize(), "NexusMissing");
        assertEquals(missing, missing.normalize());
    }

    @Test
    public void resolveAndRelativize() {
        assertType(missing.resolve("child"), "unresolved");
        assertEquals("nope.jar", missing.getParent().relativize(missing).toString());
    }

    @Test
    public void toUriAbsoluteRootFileSystem() {
        assertEquals("/content/repositories/releases-tomitribe/" + MISSING, missing.toUri().getPath());
        assertTrue(missing.isAbsolute());
        assertEquals("/", missing.getRoot().toString());
        assertEquals(root.getFileSystem(), missing.getFileSystem());
    }

    @Test
    public void toFileAndRegisterUnsupported() {
        assertThrows(UnsupportedOperationException.class, missing::toFile);
        assertThrows(UnsupportedOperationException.class,
                () -> missing.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void compareEqualsToString() {
        assertEquals(missing, root.resolve(MISSING)); // path-only identity
        assertEquals("/" + MISSING, missing.toString());
    }

    // ---- behavioral fails as NoSuchFile ---------------------------------------

    @Test
    public void readThrowsNoSuchFile() {
        assertThrows(NoSuchFileException.class, () -> Files.newInputStream(missing));
    }

    @Test
    public void listThrowsNoSuchFile() {
        assertThrows(NoSuchFileException.class, () -> Files.newDirectoryStream(missing));
    }

    @Test
    public void existsIsFalse() {
        // Files.exists swallows the exception and reports absence — exactly real usage.
        assertFalse(Files.exists(missing));
    }

    @Test
    public void readingAMissingPathDirectlyAlsoThrows() {
        // The real-world shape: just resolve and read; a 404 surfaces as NoSuchFileException.
        assertThrows(NoSuchFileException.class, () -> Files.newInputStream(root.resolve(MISSING)));
    }
}
