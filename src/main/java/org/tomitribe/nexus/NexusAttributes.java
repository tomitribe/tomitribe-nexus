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

import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * Minimal {@link BasicFileAttributes} — enough for {@code Files.walk} and {@code Files.copy}.
 *
 * <p>{@code isDirectory()} is answered with zero I/O (the path already knows its kind),
 * which is what keeps a walk request-minimal: it only ever asks "directory?". When size and
 * last-modified arrived on the listing they are returned directly. For a cold, hand-built path
 * they are filled by a single lazy HEAD — and that HEAD populates <em>both</em> size and
 * last-modified, since one response carries both, asked only if a caller actually reads them.
 */
final class NexusAttributes implements BasicFileAttributes {

    private final boolean directory;
    private final NexusPath owner;
    private Long size;
    private Instant modified;
    private boolean headed;

    NexusAttributes(final boolean directory, final Long knownSize, final Instant modified, final NexusPath owner) {
        this.directory = directory;
        this.size = knownSize;
        this.modified = modified;
        this.owner = owner;
    }

    /**
     * Fill whatever a listing didn't give us with a single HEAD. One response carries both size
     * and last-modified, so we keep both rather than throwing one away.
     */
    private void head() {
        if (directory || headed) return;
        if (size != null && modified != null) return;
        try {
            final HttpClient.Head head = owner.fs.client().head(owner.toRemoteUri());
            if (size == null) size = head.contentLength();
            if (modified == null) modified = head.lastModified();
            headed = true;
        } catch (final IOException e) {
            throw new UncheckedIOException("HEAD failed for " + owner.toRemoteUri(), e);
        }
    }

    @Override
    public boolean isRegularFile() {
        return !directory;
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        if (directory) return 0;
        head();
        return size == null ? -1 : size;
    }

    @Override
    public FileTime lastModifiedTime() {
        if (modified == null) head();
        return modified != null ? FileTime.from(modified) : FileTime.fromMillis(0);
    }

    @Override
    public FileTime lastAccessTime() {
        return lastModifiedTime();
    }

    @Override
    public FileTime creationTime() {
        return lastModifiedTime();
    }

    @Override
    public Object fileKey() {
        return null;
    }
}
