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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tomitribe.nexus.NexusAsserts.assertType;

/**
 * Every method of {@link NexusDir} — a known directory. Listing/navigation are legal;
 * reading bytes is not. Structural algebra propagates "directory" through containers.
 */
public class NexusDirTest {

    private static final URI BASE = URI.create("https://nexus.example/repo/");

    private StubHttpClient http;
    private NexusFileSystem fs;
    private NexusDir dir;

    @BeforeEach
    public void setUp() {
        http = new StubHttpClient();
        fs = new NexusFileSystem(new NexusFileSystemProvider(), BASE, http);
        dir = new NexusDir(fs, List.of("org", "apache"), true);
        assertType(dir, "NexusDir");
    }

    @Test
    public void state() {
        assertEquals("NexusDir", dir.state());
    }

    @Test
    public void directory() {
        assertTrue(dir.directory());
    }

    @Test
    public void getFileSystem() {
        assertEquals(fs, dir.getFileSystem());
    }

    @Test
    public void isAbsolute() {
        assertTrue(dir.isAbsolute());
    }

    @Test
    public void getRoot() {
        final Path root = dir.getRoot();
        assertEquals("/", root.toString());
        assertType(root, "NexusDir");
    }

    @Test
    public void getFileName() {
        final Path name = dir.getFileName();
        assertEquals("apache", name.toString());
        assertFalse(name.isAbsolute());
        assertType(name, "NexusDir");
    }

    @Test
    public void getParent() {
        final Path parent = dir.getParent();
        assertEquals("/org", parent.toString());
        assertType(parent, "NexusDir");
    }

    @Test
    public void getNameCount() {
        assertEquals(2, dir.getNameCount());
    }

    @Test
    public void getName() {
        assertEquals("org", dir.getName(0).toString());   // interior -> directory
        assertType(dir.getName(0), "NexusDir");
        assertEquals("apache", dir.getName(1).toString()); // terminal -> same kind (directory)
        assertType(dir.getName(1), "NexusDir");
    }

    @Test
    public void subpath() {
        assertEquals("org", dir.subpath(0, 1).toString());        // container prefix
        assertType(dir.subpath(0, 1), "NexusDir");
        assertEquals("org/apache", dir.subpath(0, 2).toString()); // includes terminal
        assertType(dir.subpath(0, 2), "NexusDir");
    }

    @Test
    public void startsWith() {
        assertTrue(dir.startsWith(new NexusDir(fs, List.of("org"), true)));
        assertFalse(dir.startsWith(new NexusDir(fs, List.of("com"), true)));
    }

    @Test
    public void endsWith() {
        assertTrue(dir.endsWith(new NexusDir(fs, List.of("apache"), true)));
        assertFalse(dir.endsWith(new NexusDir(fs, List.of("org"), true)));
    }

    @Test
    public void normalize() {
        final NexusDir dots = new NexusDir(fs, List.of("org", "apache", "..", "tomee"), true);
        assertEquals("/org/tomee", dots.normalize().toString());
        // chroot: leading .. cannot climb above root
        final NexusDir escape = new NexusDir(fs, List.of("..", "..", "etc"), true);
        assertEquals("/etc", escape.normalize().toString());
    }

    @Test
    public void resolve() {
        final Path child = dir.resolve(new NexusUnknown(fs, List.of("child"), false));
        assertEquals("/org/apache/child", child.toString());
        assertType(child, "unresolved");                       // fresh terminal -> unknown
        final Path absolute = new NexusDir(fs, List.of("x"), true);
        assertEquals(absolute, dir.resolve(absolute));         // absolute wins
    }

    @Test
    public void resolveSibling() {
        final Path sibling = dir.resolveSibling(new NexusUnknown(fs, List.of("tomcat"), false));
        assertEquals("/org/tomcat", sibling.toString());
    }

    @Test
    public void relativize() {
        final Path other = new NexusDir(fs, List.of("org", "apache", "tomee", "x"), true);
        final Path relative = dir.relativize(other);
        assertEquals("tomee/x", relative.toString());
        assertFalse(relative.isAbsolute());
    }

    @Test
    public void toUri() {
        assertEquals("https://nexus.example/repo/org/apache/", dir.toUri().toString());
    }

    @Test
    public void toAbsolutePath() {
        assertEquals(dir, dir.toAbsolutePath());
    }

    @Test
    public void toRealPath() {
        assertEquals(dir, dir.toRealPath());
    }

    @Test
    public void toFileUnsupported() {
        assertThrows(UnsupportedOperationException.class, dir::toFile);
    }

    @Test
    public void iterator() {
        final List<String> names = StreamSupport.stream(dir.spliterator(), false)
                .map(Path::toString).collect(Collectors.toList());
        assertEquals(List.of("org", "apache"), names);
    }

    @Test
    public void compareToOrders() {
        assertTrue(dir.compareTo(new NexusDir(fs, List.of("org", "b"), true)) < 0);
    }

    @Test
    public void equalsIsPathOnly() {
        // same path, different state -> equal (identity is path-only)
        assertEquals(dir, new NexusUnknown(fs, List.of("org", "apache"), true));
        assertEquals(dir.hashCode(), new NexusUnknown(fs, List.of("org", "apache"), true).hashCode());
        assertNotEquals(dir, new NexusDir(fs, List.of("org", "apache"), false)); // relative differs
    }

    @Test
    public void testToString() {
        assertEquals("/org/apache", dir.toString());
    }

    @Test
    public void registerUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> dir.register(null, (java.nio.file.WatchEvent.Kind<?>[]) null));
    }

    @Test
    public void openStreamRejected() {
        assertThrows(UnsupportedOperationException.class, dir::openStream);
        assertType(dir, "NexusDir");
    }

    @Test
    public void listChildren() throws Exception {
        final List<NexusPath> children = dir.listChildren();
        assertEquals(2, children.size());

        final NexusPath childDir = children.get(0);
        assertEquals("/org/apache/child-dir", childDir.toString());
        assertType(childDir, "NexusDir");

        final NexusPath childFile = children.get(1);
        assertEquals("/org/apache/child-file.jar", childFile.toString());
        assertType(childFile, "NexusFile");

        assertEquals(1, http.gets.get());
    }

    @Test
    public void attributesNeedNoRequest() throws Exception {
        final BasicFileAttributes attrs = dir.attributes();
        assertTrue(attrs.isDirectory());
        assertFalse(attrs.isRegularFile());
        assertEquals(0, attrs.size());
        assertEquals(0, http.heads.get());
        assertEquals(0, http.gets.get());
    }

    @Test
    public void checkExistsPasses() throws Exception {
        dir.checkExists();
    }

    @Test
    public void navigateToKnownDir() {
        final Path child = dir.dir("tomee/apache-tomee");
        assertEquals("/org/apache/tomee/apache-tomee", child.toString());
        assertType(child, "NexusDir");
    }

    @Test
    public void navigateToKnownFile() {
        final Path child = dir.file("tomee/maven-metadata.xml");
        assertEquals("/org/apache/tomee/maven-metadata.xml", child.toString());
        assertType(child, "NexusFile");
    }

    @Test
    public void getNameNullAtRoot() {
        assertNull(new NexusDir(fs, List.of(), true).getFileName());
        assertNull(new NexusDir(fs, List.of(), true).getParent());
    }
}
