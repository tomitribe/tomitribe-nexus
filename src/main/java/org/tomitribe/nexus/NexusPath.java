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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Base of the Nexus path lattice. Each concrete subtype is a complete, standalone
 * {@link Path} that knows what it <em>is</em> — {@link NexusDir}, {@link NexusFile},
 * {@link NexusUnknown}, {@link NexusMissing} — so legality lives in the type, not in
 * runtime {@code if (isDirectory())} checks scattered across call sites.
 *
 * <p>The filesystem is a chroot: the root {@code /} is the configured base URI and
 * {@link #normalize()} clamps {@code ..} at root, so an off-base request is
 * unrepresentable. There is no host or scheme to point elsewhere.
 *
 * <p>This class owns the structural algebra (it manipulates segments only) and the
 * path-only {@link #equals}/{@link #hashCode}. Subtypes own the behavioral methods
 * the provider routes to: {@link #openStream()}, {@link #listChildren()},
 * {@link #attributes()}, {@link #checkExists()}.
 *
 * <p>Where the algebra produces a result whose kind is structurally certain it builds
 * that kind directly: parents and interior segments are always directories. Only a
 * freshly minted terminal name ({@code resolve}, {@code getPath}) is genuinely
 * unknown — that is the one place the switching {@link NexusUnknown} is produced.
 */
abstract class NexusPath implements Path {

    final NexusFileSystem fs;
    final List<String> names;
    final boolean absolute;

    NexusPath(final NexusFileSystem fs, final List<String> names, final boolean absolute) {
        this.fs = fs;
        this.names = List.copyOf(names);
        this.absolute = absolute;
    }

    // ---- behavioral surface routed to by the provider -----------------------

    abstract InputStream openStream() throws IOException;

    abstract List<NexusPath> listChildren() throws IOException;

    abstract BasicFileAttributes attributes() throws IOException;

    abstract void checkExists() throws IOException;

    /** Current state name, used by tests to observe the {@link NexusUnknown} transition. */
    abstract String state();

    /** True when this path addresses a directory — drives the trailing slash on the wire. */
    abstract boolean directory();

    /** Build a path of the same kind as this one at the given segments. */
    abstract NexusPath sameKindAt(List<String> names, boolean absolute);

    // ---- result factories the algebra reaches for ---------------------------

    final NexusDir dirAt(final List<String> names, final boolean absolute) {
        return new NexusDir(fs, names, absolute);
    }

    final NexusUnknown unknownAt(final List<String> names, final boolean absolute) {
        return new NexusUnknown(fs, names, absolute);
    }

    /** The absolute URI to hit on the wire: base + this relative path. */
    URI toRemoteUri() {
        String relative = String.join("/", names);
        if (directory() && !names.isEmpty()) relative += "/";
        return fs.baseUri().resolve(relative);
    }

    // ---- structural algebra (segments only) ---------------------------------

    @Override
    public NexusFileSystem getFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return absolute;
    }

    @Override
    public Path getRoot() {
        return absolute ? dirAt(List.of(), true) : null;
    }

    @Override
    public Path getFileName() {
        if (names.isEmpty()) return null;
        return sameKindAt(List.of(names.get(names.size() - 1)), false);
    }

    @Override
    public Path getParent() {
        if (names.isEmpty()) return null;
        return dirAt(names.subList(0, names.size() - 1), absolute);
    }

    @Override
    public int getNameCount() {
        return names.size();
    }

    @Override
    public Path getName(final int index) {
        // Interior segments are containers; only the terminal carries this path's kind.
        final boolean terminal = index == names.size() - 1;
        final List<String> one = List.of(names.get(index));
        return terminal ? sameKindAt(one, false) : dirAt(one, false);
    }

    @Override
    public Path subpath(final int beginIndex, final int endIndex) {
        final List<String> sub = names.subList(beginIndex, endIndex);
        // Includes the terminal segment? carry this kind; otherwise it's a container prefix.
        return endIndex == names.size() ? sameKindAt(sub, false) : dirAt(sub, false);
    }

    @Override
    public boolean startsWith(final Path other) {
        final NexusPath o = cast(other);
        return o.names.size() <= names.size() && names.subList(0, o.names.size()).equals(o.names);
    }

    @Override
    public boolean endsWith(final Path other) {
        final NexusPath o = cast(other);
        return o.names.size() <= names.size()
                && names.subList(names.size() - o.names.size(), names.size()).equals(o.names);
    }

    @Override
    public Path normalize() {
        final List<String> result = new ArrayList<>();
        for (final String name : names) {
            if (name.equals(".")) continue;
            if (name.equals("..")) {
                // chroot: ".." at or above root is a no-op — cannot escape the base.
                if (!result.isEmpty()) result.remove(result.size() - 1);
                continue;
            }
            result.add(name);
        }
        return sameKindAt(result, absolute);
    }

    @Override
    public Path resolve(final Path other) {
        final NexusPath o = cast(other);
        if (o.absolute) return o;
        final List<String> result = new ArrayList<>(names);
        result.addAll(o.names);
        // A freshly appended terminal name is of unknown kind — the one switching case.
        return unknownAt(result, absolute);
    }

    @Override
    public Path resolveSibling(final Path other) {
        final Path parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    @Override
    public Path relativize(final Path other) {
        final NexusPath o = cast(other);
        if (!o.startsWith(this)) {
            throw new IllegalArgumentException("relativize supports descendants only: " + o);
        }
        return o.sameKindAt(o.names.subList(names.size(), o.names.size()), false);
    }

    @Override
    public URI toUri() {
        return toRemoteUri();
    }

    @Override
    public Path toAbsolutePath() {
        return absolute ? this : dirAt(names, true);
    }

    @Override
    public Path toRealPath(final LinkOption... options) {
        return normalize();
    }

    @Override
    public File toFile() {
        // A Nexus path is not a local file. Reaching for File here is a bug; fail loudly.
        throw new UnsupportedOperationException("NexusPath is not backed by a java.io.File: " + this);
    }

    @Override
    public Iterator<Path> iterator() {
        final List<Path> parts = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            parts.add(getName(i));
        }
        return parts.iterator();
    }

    @Override
    public int compareTo(final Path other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events,
                             final WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("watch is not supported on a Nexus filesystem");
    }

    NexusPath cast(final Path other) {
        if (!(other instanceof NexusPath o) || o.fs != this.fs) {
            throw new java.nio.file.ProviderMismatchException(String.valueOf(other));
        }
        return o;
    }

    // ---- identity is path-only, independent of state ------------------------

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof NexusPath o)) return false;
        return fs == o.fs && absolute == o.absolute && names.equals(o.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(fs), absolute, names);
    }

    @Override
    public String toString() {
        return (absolute ? "/" : "") + String.join("/", names);
    }
}
