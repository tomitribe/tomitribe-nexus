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


import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A read-only NIO FileSystem whose root {@code /} is the Nexus base URI.
 *
 * <p>Holds a small, bounded attribute cache so the natural relativize → resolve round-trip
 * (and any re-addressing of a path we already listed) avoids a redundant HEAD. The cache lives
 * here, on the filesystem — not on the paths — so paths stay pure addresses and the address
 * algebra has no metadata to lose. {@link NexusDir#listChildren} populates it; {@code resolve}
 * on a {@link NexusUnknown} consults it before reaching for the network.
 *
 * <p>It is a FIFO ("rolling") window of the most recent {@value #CACHE_CAPACITY} entries — sized
 * to the working set of a crawl (a directory's worth of children, consumed moments after listing),
 * so it stays flat regardless of how large the repository is.
 */
public final class NexusFileSystem extends FileSystem {

    private static final int CACHE_CAPACITY = 4096;

    private final NexusFileSystemProvider provider;
    private final URI baseUri;
    private final HttpClient client;
    private final String username;

    /** Absolute path names → what a listing (or a prior HEAD) told us. Bounded, FIFO, thread-safe. */
    private final Map<List<String>, CacheEntry> attributes = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_CAPACITY + 1, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<List<String>, CacheEntry> eldest) {
                    return size() > CACHE_CAPACITY;
                }
            });

    NexusFileSystem(final NexusFileSystemProvider provider, final URI baseUri, final HttpClient client, final String username) {
        this.provider = provider;
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.client = Objects.requireNonNull(client, "client");
        this.username = username;
    }

    URI baseUri() {
        return baseUri;
    }

    public URI getUri() {
        return baseUri;
    }

    public String getUsername() {
        return username;
    }

    HttpClient client() {
        return client;
    }

    /** Record what we learned about an absolute address; later re-addressing of it skips the HEAD. */
    void remember(final List<String> names, final CacheEntry entry) {
        attributes.put(List.copyOf(names), entry);
    }

    /** What we know about an absolute address, or {@code null} if we've never seen it. */
    CacheEntry recall(final List<String> names) {
        return attributes.get(names);
    }

    /** Immutable attribute snapshot from a listing or HEAD; {@code size == null} marks a directory. */
    record CacheEntry(boolean directory, Long size, Instant modified) {
    }

    Path root() {
        return new NexusDir(this, List.of(), true);
    }

    @Override
    public NexusFileSystemProvider provider() {
        return provider;
    }

    @Override
    public Path getPath(final String first, final String... more) {
        final StringBuilder sb = new StringBuilder(first);
        for (final String segment : more) {
            sb.append('/').append(segment);
        }
        // Absolute only if it begins with '/', like any provider — so resolve("child") appends
        // rather than replacing. A hand-built path's kind is not yet known, hence Unknown.
        final String joined = sb.toString();
        final boolean absolute = joined.startsWith("/");
        final List<String> names = new ArrayList<>();
        for (final String segment : joined.split("/")) {
            if (!segment.isEmpty()) names.add(segment);
        }
        return new NexusUnknown(this, names, absolute);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(root());
    }

    @Override
    public void close() {
        // nothing to release in the draft
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public PathMatcher getPathMatcher(final String syntaxAndPattern) {
        throw new UnsupportedOperationException("path matcher not implemented in draft");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        if (username != null) {
            return "NexusFileSystem{" +
                    "baseUri='" + baseUri +
                    "', username='" + username +
                    "'}";
        }
        return "NexusFileSystem{" +
                "baseUri='" + baseUri +
                "'}";
    }
}
