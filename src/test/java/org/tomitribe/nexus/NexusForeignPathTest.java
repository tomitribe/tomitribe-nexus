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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * The cases the per-state tests never exercise: a {@code Path} we have no inside knowledge
 * of. Every {@code Path}-argument method must honor the {@link Path} contract when handed
 * a foreign-provider path (a default-filesystem {@code Path}), a path from a <em>different</em>
 * {@link NexusFileSystem}, or {@code null} — rather than blowing up or, worse, returning a
 * plausible-but-meaningless answer.
 */
public class NexusForeignPathTest {

    private NexusFileSystem fs;
    private NexusDir nexus;
    private Path foreign;     // default filesystem — a different provider entirely
    private NexusPath crossFs; // a NexusPath from a different NexusFileSystem instance

    @BeforeEach
    public void setUp() {
        final URI base = URI.create("https://nexus.example/repo/");
        fs = new NexusFileSystem(new NexusFileSystemProvider(), base, new StubHttpClient());
        nexus = new NexusDir(fs, List.of("org", "apache"), true);

        foreign = Path.of("org", "apache");

        final NexusFileSystem other = new NexusFileSystem(new NexusFileSystemProvider(), base, new StubHttpClient());
        crossFs = new NexusDir(other, List.of("org", "apache"), true);
    }

    // ---- startsWith / endsWith: a relative path is segments (any provider);
    //      an absolute foreign path or null -> false (never throw) ----------------

    @Test
    public void startsWithRelativeForeignMatchesSegments() {
        assertTrue(nexus.startsWith(Path.of("org")));            // foreign relative prefix
        assertTrue(nexus.startsWith(Path.of("org/apache")));
        assertFalse(nexus.startsWith(Path.of("apache")));        // not a prefix
    }

    @Test
    public void endsWithRelativeForeignMatchesSegments() {
        // org/apache really does end with "apache" — whatever provider built that segment.
        assertTrue(nexus.endsWith(Path.of("apache")));
        assertTrue(nexus.endsWith(Path.of("org/apache")));
        assertFalse(nexus.endsWith(Path.of("org")));
    }

    @Test
    public void startsWithEndsWithAbsoluteForeignOrNullIsFalse() {
        assertFalse(nexus.startsWith(Path.of("/org/apache"))); // absolute, foreign provider
        assertFalse(nexus.endsWith(Path.of("/org/apache")));
        assertFalse(nexus.startsWith(crossFs));                // absolute, different Nexus fs
        assertFalse(nexus.endsWith(crossFs));
        assertFalse(nexus.startsWith((Path) null));
        assertFalse(nexus.endsWith((Path) null));
    }

    // ---- resolve: relative (any provider) is adopted; absolute foreign is rejected ----

    @Test
    public void resolveRelativeForeignAdoptsSegments() {
        // The motivating case: build a relative path with the ordinary Path.of(...) and
        // resolve it straight into Nexus.
        final Path resolved = nexus.resolve(Path.of("tomee", "apache-tomee"));
        assertEquals("/org/apache/tomee/apache-tomee", resolved.toString());
        assertType(resolved, "unresolved");

        // ...even when the segments were produced on a default filesystem with its own separator.
        assertEquals("/org/apache/x/y/z", nexus.resolve(Path.of("x/y/z")).toString());
    }

    @Test
    public void resolveAbsoluteForeignThrowsProviderMismatch() {
        assertThrows(ProviderMismatchException.class, () -> nexus.resolve(Path.of("/etc/passwd")));
        assertThrows(ProviderMismatchException.class, () -> nexus.resolve(crossFs)); // absolute, different fs
    }

    @Test
    public void resolveNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> nexus.resolve((Path) null));
    }

    @Test
    public void relativizeForeignThrowsProviderMismatch() {
        assertThrows(ProviderMismatchException.class, () -> nexus.relativize(foreign));
        assertThrows(ProviderMismatchException.class, () -> nexus.relativize(crossFs));
    }

    @Test
    public void relativizeNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> nexus.relativize((Path) null));
    }

    // ---- compareTo: foreign -> ClassCastException, null -> NPE ---------------

    @Test
    public void compareToForeignThrowsClassCast() {
        assertThrows(ClassCastException.class, () -> nexus.compareTo(foreign));
        assertThrows(ClassCastException.class, () -> nexus.compareTo(crossFs));
    }

    @Test
    public void compareToNullThrowsNpe() {
        assertThrows(NullPointerException.class, () -> nexus.compareTo(null));
    }

    // ---- equals: foreign / cross-fs / null -> not equal (never throw) --------

    @Test
    public void equalsForeignIsFalse() {
        assertFalse(nexus.equals(foreign));
        assertFalse(nexus.equals(crossFs)); // same path, different filesystem -> not equal
        assertFalse(nexus.equals(null));
    }
}
