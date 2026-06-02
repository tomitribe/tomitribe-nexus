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

import org.apache.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;

/**
 * A known file. Supports reading bytes; listing is illegal.
 *
 * <p>Named for what it becomes, not what it technically is: when you download it to
 * disk you get a file. It may carry a size already known from the request that
 * produced it; otherwise size is fetched lazily via HEAD on demand.
 */
final class NexusFile extends NexusPath {

    private final Long size;
    private final Instant modified;

    NexusFile(final NexusFileSystem fs, final List<String> names, final boolean absolute,
              final Long size, final Instant modified) {
        super(fs, names, absolute);
        this.size = size;
        this.modified = modified;
    }

    @Override
    boolean directory() {
        return false;
    }

    @Override
    NexusPath sameKindAt(final List<String> names, final boolean absolute) {
        // A derived path (getFileName/subpath) is a different entity — its size/date are unknown.
        return new NexusFile(fs, names, absolute, null, null);
    }

    @Override
    InputStream openStream() throws IOException {
        final HttpResponse response = fs.client().get(toRemoteUri());
        final int status = response.getStatusLine().getStatusCode();
        if (status == 404) throw new NoSuchFileException(toString());
        if (status != 200) throw new IOException("GET " + toRemoteUri() + " -> " + status);
        return response.getEntity().getContent();
    }

    @Override
    List<NexusPath> listChildren() {
        throw new UnsupportedOperationException("Not a directory: " + this);
    }

    @Override
    BasicFileAttributes attributes() {
        return new NexusAttributes(false, size, modified, this);
    }

    @Override
    void checkExists() {
        // Produced from a listing; existence is implied. A cold HEAD could confirm.
    }
}
