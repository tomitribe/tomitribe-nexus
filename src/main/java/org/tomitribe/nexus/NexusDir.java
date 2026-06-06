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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.tomitribe.nexus.parse.Parser;
import org.tomitribe.nexus.parse.Parsers;
import org.tomitribe.util.IO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A known directory. Supports listing/walking; reading bytes is illegal.
 *
 * <p>Children are discovered by scraping the HTML index via the pluggable {@link Parsers}.
 * The listing itself tells us each child's kind — a trailing slash means a directory — and
 * carries its size and last-modified, so children arrive as concrete, immutable
 * {@link NexusDir}/{@link NexusFile} with their attributes and no extra request.
 */
final class NexusDir extends NexusPath {

    private final Instant modified;

    NexusDir(final NexusFileSystem fs, final List<String> names, final boolean absolute) {
        this(fs, names, absolute, null);
    }

    NexusDir(final NexusFileSystem fs, final List<String> names, final boolean absolute, final Instant modified) {
        super(fs, names, absolute);
        this.modified = modified;
    }

    /**
     * Navigate to a child directory the caller already knows to be one (e.g. a product
     * path). Unlike {@link #resolve(java.nio.file.Path)} — which yields a switching
     * {@link NexusUnknown} because a bare name's kind is unknown — this asserts the kind.
     */
    NexusDir dir(final String relative) {
        return new NexusDir(fs, append(relative), true);
    }

    /** Navigate to a child file the caller already knows to be one. */
    NexusFile file(final String relative) {
        return new NexusFile(fs, append(relative), true, null, null);
    }

    private List<String> append(final String relative) {
        final List<String> result = new ArrayList<>(names);
        for (final String segment : relative.split("/")) {
            if (!segment.isEmpty()) result.add(segment);
        }
        return result;
    }

    @Override
    boolean directory() {
        return true;
    }

    @Override
    NexusPath sameKindAt(final List<String> names, final boolean absolute) {
        return new NexusDir(fs, names, absolute);
    }

    @Override
    InputStream openStream() {
        throw new UnsupportedOperationException("Is a directory: " + this);
    }

    @Override
    List<NexusPath> listChildren() throws IOException {
        final CloseableHttpResponse response = fs.client().get(toRemoteUri());
        final int status = response.getStatusLine().getStatusCode();
        if (status != 200) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new IOException("Listing " + toRemoteUri() + " -> " + status);
        }
        final List<NexusPath> children = new ArrayList<>();
        final String content;
        // Closing the entity stream returns the connection to the pool.
        try (final InputStream in = response.getEntity().getContent()) {
            content = IO.slurp(in);
        }

        final List<Parser.Node> nodes = Parsers.parse(content);
        for (final Parser.Node node : nodes) {
            final String name = node.getName();

            final List<String> childNames = new ArrayList<>(names);
            if (name.endsWith("/")) {
                childNames.add(name.substring(0, name.length() - 1));
                children.add(new NexusDir(fs, childNames, true, node.getModified()));
                fs.remember(childNames, new NexusFileSystem.CacheEntry(true, null, node.getModified()));
            } else {
                childNames.add(name);
                children.add(new NexusFile(fs, childNames, true, node.getSize(), node.getModified()));
                fs.remember(childNames, new NexusFileSystem.CacheEntry(false, node.getSize(), node.getModified()));
            }
        }
        return children;
    }

    @Override
    BasicFileAttributes attributes() {
        // Known directory — no request needed; carries the listing's date when we have it.
        return new NexusAttributes(true, null, modified, this);
    }

    @Override
    void checkExists() {
        // A directory handle is only ever produced from a listing or known structure.
    }
}
