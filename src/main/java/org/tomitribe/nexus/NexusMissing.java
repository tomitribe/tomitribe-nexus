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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * A path that resolved to a 404. Every access fails with {@link NoSuchFileException},
 * carrying the path so the message is actionable.
 */
final class NexusMissing extends NexusPath {

    NexusMissing(final NexusFileSystem fs, final List<String> names, final boolean absolute) {
        super(fs, names, absolute);
    }

    @Override
    boolean directory() {
        return false;
    }

    @Override
    NexusPath sameKindAt(final List<String> names, final boolean absolute) {
        return new NexusUnknown(fs, names, absolute);
    }

    @Override
    InputStream openStream() throws IOException {
        throw new NoSuchFileException(toString());
    }

    @Override
    List<NexusPath> listChildren() throws IOException {
        throw new NoSuchFileException(toString());
    }

    @Override
    BasicFileAttributes attributes() throws IOException {
        throw new NoSuchFileException(toString());
    }

    @Override
    void checkExists() throws IOException {
        throw new NoSuchFileException(toString());
    }

    /**
     * Keep the missing kind. {@code normalize()} preserves the same path (just tidied), so a
     * resolved-to-missing handle stays observably {@code NexusMissing} — unlike {@link #sameKindAt}
     * (used by getFileName/subpath), which derives a <em>different</em> path of unknown kind.
     */
    @Override
    public Path normalize() {
        return new NexusMissing(fs, normalizeNames(names), absolute);
    }
}
