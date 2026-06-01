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

/**
 * Minimal {@link BasicFileAttributes} — enough for {@code Files.walk} and {@code Files.copy}.
 *
 * <p>{@code isDirectory()} is answered with zero I/O (the path already knows its kind),
 * which is what keeps a walk request-minimal: it only ever asks "directory?". Size is
 * resolved lazily via a HEAD and only if a caller actually asks — a walk never does.
 */
final class NexusAttributes implements BasicFileAttributes {

    private final boolean directory;
    private final NexusPath owner;
    private Long size;

    NexusAttributes(final boolean directory, final long size, final NexusPath owner) {
        this.directory = directory;
        this.size = size;
        this.owner = owner;
    }

    NexusAttributes(final boolean directory, final Long knownSize, final NexusPath owner) {
        this.directory = directory;
        this.size = knownSize;
        this.owner = owner;
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
        if (size == null) {
            try {
                size = owner.fs.client().getContentLength(owner.toRemoteUri());
            } catch (final IOException e) {
                throw new UncheckedIOException("HEAD failed for " + owner.toRemoteUri(), e);
            }
        }
        return size;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.fromMillis(0);
    }

    @Override
    public FileTime lastAccessTime() {
        return FileTime.fromMillis(0);
    }

    @Override
    public FileTime creationTime() {
        return FileTime.fromMillis(0);
    }

    @Override
    public Object fileKey() {
        return null;
    }
}
