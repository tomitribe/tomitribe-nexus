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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tomitribe.nexus.Nexus;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Black box. The {@link Path} contract exercised entirely through the public API — no server
 * needed, since path algebra touches no network. Relative-vs-absolute and same-provider-vs-not
 * are all reachable from {@link Nexus#builder()} plus {@code Path.of(...)} and a second root.
 */
public class NexusPathContractTest {

    private static final URI BASE = URI.create("https://nexus.example/repo/");

    private Path root;

    @BeforeEach
    public void setUp() {
        root = Nexus.builder().baseUri(BASE).build(); // anonymous; no requests for pure algebra
    }

    @Test
    public void relativeVersusAbsolute() {
        final Path p = root.resolve("org/apache/tomee");
        assertTrue(p.isAbsolute());
        assertFalse(p.getFileName().isAbsolute());
        assertEquals("/org/apache/tomee", p.toString());
        assertEquals("tomee", p.getFileName().toString());
    }

    @Test
    public void resolveAdoptsRelativePathOf() {
        // The motivating case: build a relative path with ordinary NIO and resolve it into Nexus.
        final Path p = root.resolve(Path.of("org/apache/tomee/apache-tomee"));
        assertEquals("/org/apache/tomee/apache-tomee", p.toString());
        assertType(p, "unresolved");
    }

    @Test
    public void resolveAbsoluteForeignIsRejected() {
        assertThrows(ProviderMismatchException.class, () -> root.resolve(Path.of("/etc/passwd")));
    }

    @Test
    public void resolvesARelativePathFromAWindowsProvider() throws Exception {
        // A user on Windows builds Path.of("org/apache/tomee/apache-tomee/"); the default provider
        // converts it to backslash separators. Jimfs gives us that exact provider on any OS.
        try (FileSystem windows = Jimfs.newFileSystem(Configuration.windows())) {
            final Path winRelative = windows.getPath("org/apache/tomee/apache-tomee/");

            // It really is a backslash-separated, foreign, relative path.
            assertEquals("org\\apache\\tomee\\apache-tomee", winRelative.toString());
            assertFalse(winRelative.isAbsolute());

            // We adopt name elements, not toString(), so the separator is irrelevant — it resolves.
            final Path resolved = root.resolve(winRelative);
            assertEquals("/org/apache/tomee/apache-tomee", resolved.toString());
            assertType(resolved, "unresolved");

            // startsWith/endsWith go through the same segment path, so they work too.
            assertTrue(resolved.startsWith(windows.getPath("org/apache")));
            assertTrue(resolved.endsWith(windows.getPath("apache-tomee")));
        }
    }

    @Test
    public void startsWithEndsWithBySegments() {
        final Path p = root.resolve("org/apache/tomee");
        assertTrue(p.startsWith(Path.of("org/apache")));
        assertTrue(p.endsWith(Path.of("tomee")));            // org/apache/tomee really does end with tomee
        assertTrue(p.endsWith(Path.of("apache/tomee")));
        assertFalse(p.endsWith(Path.of("apache")));
    }

    @Test
    public void compareToForeignThrowsClassCast() {
        assertThrows(ClassCastException.class, () -> root.compareTo(Path.of("/x")));
    }

    @Test
    public void equalsForeignIsFalse() {
        assertFalse(root.resolve("a/b").equals(Path.of("a/b")));
    }

    @Test
    public void differentRootIsADifferentProvider() {
        final Path other = Nexus.builder().baseUri(BASE).build(); // same base, distinct filesystem
        assertFalse(root.equals(other));
        assertThrows(ProviderMismatchException.class, () -> root.resolve(other)); // other is absolute
    }

    @Test
    public void normalizePeekIsLazyAndChroots() {
        final Path p = root.resolve("a/b");
        assertType(p, "unresolved");                          // peeking never resolves
        assertEquals("/repo/etc/passwd",
                root.resolve("a/../../../../etc/passwd").normalize().toUri().getPath()); // .. clamps to base
    }
}
