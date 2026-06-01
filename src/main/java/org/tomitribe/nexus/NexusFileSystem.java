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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * DRAFT — a read-only NIO FileSystem whose root {@code /} is the Nexus base URI.
 */
final class NexusFileSystem extends FileSystem {

    private final NexusFileSystemProvider provider;
    private final URI baseUri;
    private final HttpClient client;

    NexusFileSystem(final NexusFileSystemProvider provider, final URI baseUri, final HttpClient client) {
        this.provider = provider;
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.client = Objects.requireNonNull(client, "client");
    }

    URI baseUri() {
        return baseUri;
    }

    HttpClient client() {
        return client;
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
        // A hand-built path's kind is not yet known — hence Unknown.
        final List<String> names = new ArrayList<>();
        for (final String segment : sb.toString().split("/")) {
            if (!segment.isEmpty()) names.add(segment);
        }
        return new NexusUnknown(this, names, true);
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
}
