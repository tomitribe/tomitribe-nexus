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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every method of {@link NexusMissing} — a path that resolved to a 404. Every behavioral
 * access fails with {@link NoSuchFileException}; structural algebra still works (a missing
 * path has a perfectly good name and parent), and touches no network.
 */
public class NexusMissingTest {

    private static final URI BASE = URI.create("https://nexus.example/repo/");

    private StubHttpClient http;
    private NexusFileSystem fs;
    private NexusMissing missing;

    @BeforeEach
    public void setUp() {
        http = new StubHttpClient();
        fs = new NexusFileSystem(new NexusFileSystemProvider(), BASE, http);
        missing = new NexusMissing(fs, List.of("org", "missing"), true);
        assertType(missing, "NexusMissing");
    }

    @Test
    public void state() {
        assertEquals("NexusMissing", missing.state());
    }

    @Test
    public void directory() {
        assertFalse(missing.directory());
    }

    @Test
    public void openStreamThrows() {
        assertThrows(NoSuchFileException.class, missing::openStream);
    }

    @Test
    public void listChildrenThrows() {
        assertThrows(NoSuchFileException.class, missing::listChildren);
    }

    @Test
    public void attributesThrows() {
        assertThrows(NoSuchFileException.class, missing::attributes);
    }

    @Test
    public void checkExistsThrows() {
        assertThrows(NoSuchFileException.class, missing::checkExists);
    }

    @Test
    public void behavioralFailuresTouchNoNetwork() {
        assertThrows(NoSuchFileException.class, missing::openStream);
        assertThrows(NoSuchFileException.class, missing::attributes);
        assertEquals(0, http.heads.get());
        assertEquals(0, http.gets.get());
    }

    @Test
    public void getParent() {
        final Path parent = missing.getParent();
        assertEquals("/org", parent.toString());
        assertType(parent, "NexusDir");
    }

    @Test
    public void getFileName() {
        final Path name = missing.getFileName();
        assertEquals("missing", name.toString());
        assertType(name, "unresolved"); // sameKindAt for a missing path falls back to unknown
    }

    @Test
    public void getNameCount() {
        assertEquals(2, missing.getNameCount());
    }

    @Test
    public void getNameAndSubpath() {
        assertEquals("org", missing.getName(0).toString());
        assertEquals("org/missing", missing.subpath(0, 2).toString());
    }

    @Test
    public void normalize() {
        final NexusMissing dots = new NexusMissing(fs, List.of("org", "x", "..", "missing"), true);
        assertEquals("/org/missing", dots.normalize().toString());
    }

    @Test
    public void resolve() {
        final Path child = missing.resolve(new NexusUnknown(fs, List.of("child"), false));
        assertEquals("/org/missing/child", child.toString());
        assertType(child, "unresolved");
    }

    @Test
    public void toUri() {
        assertEquals("https://nexus.example/repo/org/missing", missing.toUri().toString());
    }

    @Test
    public void toFileUnsupported() {
        assertThrows(UnsupportedOperationException.class, missing::toFile);
    }

    @Test
    public void registerUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> missing.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void testToString() {
        assertEquals("/org/missing", missing.toString());
    }

    @Test
    public void equalsIsPathOnly() {
        assertEquals(missing, new NexusUnknown(fs, List.of("org", "missing"), true));
        assertEquals(missing.hashCode(), new NexusDir(fs, List.of("org", "missing"), true).hashCode());
    }
}
