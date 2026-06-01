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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deliberately thin NIO provider. Textbook NIO puts behavior here and keeps {@link Path}
 * dumb; we invert that — the path states ({@link NexusDir}/{@link NexusFile}/etc.) carry
 * the behavior and per-state legality, and this provider just routes the SPI calls to
 * them. That inversion is what lets each state be a complete, independently testable
 * {@link Path}. The next reader should expect smart paths, not a smart provider.
 *
 * <p>Read-only: every mutating method throws {@link ReadOnlyFileSystemException}.
 * Self-instantiated by {@link Nexus}; never registered via {@code META-INF/services},
 * so {@code getPath(URI)}/{@code getFileSystem(URI)} are not entry points.
 */
final class NexusFileSystemProvider extends FileSystemProvider {

    @Override
    public String getScheme() {
        return "nexus";
    }

    // ---- read surface: route to the path state ------------------------------

    @Override
    public InputStream newInputStream(final Path path, final OpenOption... options) throws IOException {
        return ((NexusPath) path).openStream();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        final List<NexusPath> children = ((NexusPath) dir).listChildren();
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return children.stream()
                        .map(child -> (Path) child)
                        .filter(child -> accept(filter, child))
                        .iterator();
            }

            @Override
            public void close() {
            }
        };
    }

    private static boolean accept(final DirectoryStream.Filter<? super Path> filter, final Path child) {
        if (filter == null) return true;
        try {
            return filter.accept(child);
        } catch (final IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type,
                                                            final LinkOption... options) throws IOException {
        return (A) ((NexusPath) path).attributes();
    }

    @Override
    public void checkAccess(final Path path, final AccessMode... modes) throws IOException {
        for (final AccessMode mode : modes) {
            if (mode == AccessMode.WRITE || mode == AccessMode.EXECUTE) {
                throw new AccessDeniedException(path.toString(), null, "Nexus filesystem is read-only");
            }
        }
        ((NexusPath) path).checkExists();
    }

    @Override
    public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options,
                                              final FileAttribute<?>... attrs) {
        // Files.copy uses newInputStream, so a channel isn't on the hot path. A forward-only
        // read channel is straightforward to add when a caller needs one.
        throw new UnsupportedOperationException("newByteChannel not implemented; use newInputStream");
    }

    @Override
    public boolean isSameFile(final Path path, final Path path2) {
        return path.equals(path2);
    }

    @Override
    public boolean isHidden(final Path path) {
        return false;
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type,
                                                                final LinkOption... options) {
        return null;
    }

    @Override
    public Map<String, Object> readAttributes(final Path path, final String attributes, final LinkOption... options) {
        throw new UnsupportedOperationException("string-form attributes not supported");
    }

    @Override
    public FileStore getFileStore(final Path path) {
        throw new UnsupportedOperationException("no file store");
    }

    // ---- not registered globally --------------------------------------------

    @Override
    public FileSystem getFileSystem(final URI uri) {
        throw new UnsupportedOperationException("obtain the FileSystem from Nexus.root(...)");
    }

    @Override
    public Path getPath(final URI uri) {
        throw new UnsupportedOperationException("obtain paths from the FileSystem");
    }

    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) {
        throw new UnsupportedOperationException("Nexus.root(...) constructs the FileSystem");
    }

    // ---- read-only -----------------------------------------------------------

    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void delete(final Path path) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void copy(final Path source, final Path target, final CopyOption... options) {
        // within-provider copy; cross-provider (Nexus -> local) goes through newInputStream
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void move(final Path source, final Path target, final CopyOption... options) {
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void setAttribute(final Path path, final String attribute, final Object value, final LinkOption... options) {
        throw new ReadOnlyFileSystemException();
    }
}
