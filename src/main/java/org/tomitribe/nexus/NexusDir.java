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
import org.tomitribe.swizzle.stream.StreamLexer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * A known directory. Supports listing/walking; reading bytes is illegal.
 *
 * <p>Children are discovered by scraping the Nexus HTML index (the same
 * {@link StreamLexer} lex the old {@code Crawler} used). The listing itself tells us
 * each child's kind — a trailing slash means a directory — so children arrive as
 * concrete, immutable {@link NexusDir}/{@link NexusFile} with no extra request.
 */
final class NexusDir extends NexusPath {

    NexusDir(final NexusFileSystem fs, final List<String> names, final boolean absolute) {
        super(fs, names, absolute);
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
        return new NexusFile(fs, append(relative), true, null);
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
        final HttpResponse response = fs.client().get(toRemoteUri());
        final int status = response.getStatusLine().getStatusCode();
        if (status != 200) {
            throw new IOException("Listing " + toRemoteUri() + " -> " + status);
        }
        final List<NexusPath> children = new ArrayList<>();
        try (final InputStream content = response.getEntity().getContent()) {
            final StreamLexer lexer = new StreamLexer(content);
            while (lexer.readAndMark("<a ", "/a>")) {
                try {
                    final String link = lexer.peek("href=\"", "\"");
                    final String name = lexer.peek(">", "<");
                    if (name.equals("../") || link.equals("../")) continue;

                    final List<String> childNames = new ArrayList<>(names);
                    if (name.endsWith("/")) {
                        childNames.add(name.substring(0, name.length() - 1));
                        children.add(new NexusDir(fs, childNames, true));
                    } else {
                        childNames.add(name);
                        children.add(new NexusFile(fs, childNames, true, null));
                    }
                } finally {
                    lexer.unmark();
                }
            }
        }
        return children;
    }

    @Override
    BasicFileAttributes attributes() {
        // Known directory — no request needed.
        return new NexusAttributes(true, 0, this);
    }

    @Override
    void checkExists() {
        // A directory handle is only ever produced from a listing or known structure.
    }

    @Override
    String state() {
        return "NexusDir";
    }
}
