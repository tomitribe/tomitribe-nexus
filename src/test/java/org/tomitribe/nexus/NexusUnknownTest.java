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

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every method of {@link NexusUnknown} — the one switching state. Structural algebra
 * never triggers a request; the first behavioral call resolves (a single HEAD) and the
 * handle adopts a concrete state, observable via {@link NexusAsserts#assertType}.
 */
public class NexusUnknownTest {

    private static final URI BASE = URI.create("https://nexus.example/repo/");

    private StubHttpClient http;
    private NexusFileSystem fs;
    private NexusUnknown unknown;

    @BeforeEach
    public void setUp() {
        http = new StubHttpClient();
        fs = new NexusFileSystem(new NexusFileSystemProvider(), BASE, http);
        unknown = new NexusUnknown(fs, List.of("org", "apache"), true);
        assertType(unknown, "unresolved");
    }

    @Test
    public void state() {
        assertEquals("unresolved", unknown.state());
    }

    @Test
    public void directory() {
        assertFalse(unknown.directory()); // probes without a trailing slash until resolved
    }

    @Test
    public void getFileSystem() {
        assertEquals(fs, unknown.getFileSystem());
    }

    @Test
    public void isAbsolute() {
        assertTrue(unknown.isAbsolute());
    }

    @Test
    public void structuralAlgebraDoesNotResolve() {
        assertEquals("/org", unknown.getParent().toString());
        assertType(unknown.getParent(), "NexusDir"); // parents are always directories
        assertEquals("apache", unknown.getFileName().toString());
        assertType(unknown.getFileName(), "unresolved"); // terminal keeps the unknown kind
        assertEquals(2, unknown.getNameCount());
        assertEquals("org", unknown.getName(0).toString());
        assertEquals("org/apache", unknown.subpath(0, 2).toString());
        assertEquals("/", unknown.getRoot().toString());
        assertEquals("https://nexus.example/repo/org/apache", unknown.toUri().toString());
        assertEquals("/org/apache", unknown.toString());

        assertEquals(0, http.heads.get(), "structural algebra must not hit the network");
        assertEquals(0, http.gets.get());
    }

    @Test
    public void resolveToDirectory() throws Exception {
        assertTrue(unknown.attributes().isDirectory());
        assertType(unknown, "NexusDir");
    }

    @Test
    public void resolveToFile() throws Exception {
        final NexusUnknown file = new NexusUnknown(fs, List.of("org", "apache", "foo.jar"), true);
        assertTrue(file.attributes().isRegularFile());
        assertType(file, "NexusFile");
    }

    @Test
    public void resolveToMissing() {
        final NexusUnknown missing = new NexusUnknown(fs, List.of("org", "missing"), true);
        assertThrows(NoSuchFileException.class, missing::attributes);
        assertType(missing, "NexusMissing");
    }

    @Test
    public void resolvesAtMostOnce() throws Exception {
        unknown.attributes();
        unknown.attributes();
        unknown.checkExists();
        assertEquals(1, http.heads.get(), "resolution must be cached after the first HEAD");
    }

    @Test
    public void openStreamResolvesAndReads() throws Exception {
        final NexusUnknown file = new NexusUnknown(fs, List.of("org", "apache", "foo.jar"), true);
        try (final InputStream in = file.openStream()) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertType(file, "NexusFile");
    }

    @Test
    public void openStreamOnDirectoryRejected() {
        assertThrows(UnsupportedOperationException.class, unknown::openStream); // resolves to dir, which rejects read
        assertType(unknown, "NexusDir");
    }

    @Test
    public void listChildrenResolvesAndLists() throws Exception {
        final List<NexusPath> children = unknown.listChildren();
        assertEquals(2, children.size());
        assertType(unknown, "NexusDir");
    }

    @Test
    public void checkExistsOnMissingThrows() {
        final NexusUnknown missing = new NexusUnknown(fs, List.of("org", "missing"), true);
        assertThrows(NoSuchFileException.class, missing::checkExists);
        assertType(missing, "NexusMissing");
    }

    @Test
    public void normalizeStaysUnknown() {
        final NexusUnknown dots = new NexusUnknown(fs, List.of("org", "x", "..", "foo"), true);
        assertEquals("/org/foo", dots.normalize().toString());
        assertType(dots.normalize(), "unresolved");
    }

    @Test
    public void resolveAndRelativize() {
        assertEquals("/org/apache/child",
                unknown.resolve(new NexusUnknown(fs, List.of("child"), false)).toString());
        final Path other = new NexusUnknown(fs, List.of("org", "apache", "x"), true);
        assertEquals("x", unknown.relativize(other).toString());
    }

    @Test
    public void toFileUnsupported() {
        assertThrows(UnsupportedOperationException.class, unknown::toFile);
    }

    @Test
    public void registerUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> unknown.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void equalsIsPathOnly() {
        assertEquals(unknown, new NexusDir(fs, List.of("org", "apache"), true));
        assertEquals(unknown.hashCode(), new NexusDir(fs, List.of("org", "apache"), true).hashCode());
    }

    @Test
    public void offBaseIsUnrepresentable() {
        // .. tries to climb above the base; chroot clamps it inside the repo, never the host root.
        final NexusUnknown escape = new NexusUnknown(fs, List.of("a", "..", "..", "..", "etc", "passwd"), true);
        final Path normalized = escape.normalize();
        assertEquals("https://nexus.example/repo/etc/passwd", normalized.toUri().toString());
        assertTrue(normalized.toUri().getPath().startsWith("/repo/"));
    }
}
