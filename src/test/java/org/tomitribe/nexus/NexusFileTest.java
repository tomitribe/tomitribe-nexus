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
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every method of {@link NexusFile} — a known file. Reading is legal; listing is not.
 * Size is fetched lazily (a single HEAD) unless already known from the listing.
 */
public class NexusFileTest {

    private static final URI BASE = URI.create("https://nexus.example/repo/");

    private StubHttpClient http;
    private NexusFileSystem fs;
    private NexusFile file;

    @BeforeEach
    public void setUp() {
        http = new StubHttpClient();
        fs = new NexusFileSystem(new NexusFileSystemProvider(), BASE, http);
        file = new NexusFile(fs, List.of("org", "apache", "foo.jar"), true, null);
        assertType(file, "NexusFile");
    }

    @Test
    public void state() {
        assertEquals("NexusFile", file.state());
    }

    @Test
    public void directory() {
        assertFalse(file.directory());
    }

    @Test
    public void getFileName() {
        final Path name = file.getFileName();
        assertEquals("foo.jar", name.toString());
        assertType(name, "NexusFile"); // terminal carries the file kind
    }

    @Test
    public void getParent() {
        final Path parent = file.getParent();
        assertEquals("/org/apache", parent.toString());
        assertType(parent, "NexusDir"); // a parent is always a directory
    }

    @Test
    public void getNameCount() {
        assertEquals(3, file.getNameCount());
    }

    @Test
    public void getName() {
        assertType(file.getName(0), "NexusDir");  // interior -> directory
        assertType(file.getName(2), "NexusFile"); // terminal -> file
        assertEquals("foo.jar", file.getName(2).toString());
    }

    @Test
    public void subpath() {
        assertType(file.subpath(0, 2), "NexusDir");          // container prefix
        assertEquals("org/apache", file.subpath(0, 2).toString());
        assertType(file.subpath(0, 3), "NexusFile");         // includes terminal
        assertEquals("org/apache/foo.jar", file.subpath(0, 3).toString());
    }

    @Test
    public void startsWithEndsWith() {
        assertTrue(file.startsWith(new NexusDir(fs, List.of("org", "apache"), true)));
        assertTrue(file.endsWith(new NexusFile(fs, List.of("foo.jar"), false, null)));
    }

    @Test
    public void normalize() {
        final NexusFile dots = new NexusFile(fs, List.of("org", "x", "..", "foo.jar"), true, null);
        assertEquals("/org/foo.jar", dots.normalize().toString());
        assertType(dots.normalize(), "NexusFile");
    }

    @Test
    public void resolve() {
        final Path child = file.resolve(new NexusUnknown(fs, List.of("child"), false));
        assertEquals("/org/apache/foo.jar/child", child.toString());
        assertType(child, "unresolved");
    }

    @Test
    public void relativize() {
        final Path other = new NexusFile(fs, List.of("org", "apache", "foo.jar", "x"), true, null);
        assertEquals("x", file.relativize(other).toString());
    }

    @Test
    public void toUri() {
        assertEquals("https://nexus.example/repo/org/apache/foo.jar", file.toUri().toString());
    }

    @Test
    public void toAbsolutePathAndRealPath() {
        assertEquals(file, file.toAbsolutePath());
        assertEquals(file, file.toRealPath());
    }

    @Test
    public void toFileUnsupported() {
        assertThrows(UnsupportedOperationException.class, file::toFile);
    }

    @Test
    public void compareToAndEquals() {
        assertTrue(file.compareTo(new NexusFile(fs, List.of("org", "apache", "z"), true, null)) < 0);
        assertEquals(file, new NexusUnknown(fs, List.of("org", "apache", "foo.jar"), true));
    }

    @Test
    public void testToString() {
        assertEquals("/org/apache/foo.jar", file.toString());
    }

    @Test
    public void registerUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> file.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void openStreamReads() throws Exception {
        try (final InputStream in = file.openStream()) {
            assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals(1, http.gets.get());
        assertType(file, "NexusFile");
    }

    @Test
    public void listChildrenRejected() {
        assertThrows(UnsupportedOperationException.class, file::listChildren);
        assertType(file, "NexusFile");
    }

    @Test
    public void attributesSizeIsLazy() throws Exception {
        final BasicFileAttributes attrs = file.attributes();
        assertTrue(attrs.isRegularFile());
        assertFalse(attrs.isDirectory());
        assertEquals(0, http.heads.get());      // no request just to describe kind

        assertEquals(5, attrs.size());           // size triggers a single HEAD
        assertEquals(1, http.heads.get());
    }

    @Test
    public void attributesSizeFromListingNeedsNoRequest() throws Exception {
        final NexusFile known = new NexusFile(fs, List.of("org", "apache", "foo.jar"), true, 42L);
        assertEquals(42, known.attributes().size());
        assertEquals(0, http.heads.get());
    }

    @Test
    public void checkExistsPasses() throws Exception {
        file.checkExists();
    }
}
