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
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one switching state: a path whose kind is not yet known — minted by
 * {@code resolve(name)} or {@code getPath(...)}, where no listing has told us whether
 * the target is a file, a directory, or absent.
 *
 * <p>On first behavioral access it resolves itself with a single HEAD and atomically
 * adopts a concrete delegate ({@link NexusDir}, {@link NexusFile}, or
 * {@link NexusMissing}), caching it so the request happens at most once. Its identity
 * (segments) never changes — only the learned kind — so {@code equals}/{@code hashCode}
 * stay stable, exactly as jaws' {@code Unknown -> Metadata|NewObject} transition.
 *
 * <p>The resolved delegate is observable via {@link #state()} so tests can assert the
 * transition (see {@code NexusAsserts}).
 */
final class NexusUnknown extends NexusPath {

    private final AtomicReference<NexusPath> resolved = new AtomicReference<>();

    NexusUnknown(final NexusFileSystem fs, final List<String> names, final boolean absolute) {
        super(fs, names, absolute);
    }

    @Override
    boolean directory() {
        // Unknown until resolved; the wire URI probes without a trailing slash.
        return false;
    }

    @Override
    NexusPath sameKindAt(final List<String> names, final boolean absolute) {
        return new NexusUnknown(fs, names, absolute);
    }

    private NexusPath resolve() throws IOException {
        final NexusPath current = resolved.get();
        if (current != null) return current;

        // head() releases the connection itself — no leak even though we discard the rest.
        final HttpClient.Head head = fs.client().head(toRemoteUri());

        final NexusPath discovered;
        if (head.status() == 404) {
            discovered = new NexusMissing(fs, names, absolute);
        } else if (isDirectory(head)) {
            discovered = new NexusDir(fs, names, absolute);
        } else {
            discovered = new NexusFile(fs, names, absolute, head.contentLength(), head.lastModified());
        }

        resolved.compareAndSet(null, discovered);
        return resolved.get();
    }

    private static boolean isDirectory(final HttpClient.Head head) {
        // Nexus 2 quirk: a directory HEAD is 200 with NO Content-Type and Content-Length: 0,
        // whereas a file always carries a content type and a non-zero length. Pin this to the
        // Server header so we don't misread a genuinely empty, typeless file elsewhere.
        if (head.isNexus2()) {
            return head.contentType() == null && (head.contentLength() == null || head.contentLength() == 0L);
        }

        // Autoindex/Central/Nexus 3 serve a directory listing as text/html.
        return head.isHtml();
    }

    @Override
    InputStream openStream() throws IOException {
        return resolve().openStream();
    }

    @Override
    List<NexusPath> listChildren() throws IOException {
        return resolve().listChildren();
    }

    @Override
    BasicFileAttributes attributes() throws IOException {
        return resolve().attributes();
    }

    @Override
    void checkExists() throws IOException {
        resolve().checkExists();
    }

    /**
     * Peek, never resolve. Once this path has been resolved (by a prior behavioral call),
     * {@code normalize()} returns the concrete state — {@link NexusDir}/{@link NexusFile}/
     * {@link NexusMissing} — so callers can observe the transition via the returned path's
     * runtime class without triggering a request. Until then it returns the unresolved path.
     * This is the deliberate, documented hook tests use to assert state through the public API.
     */
    @Override
    public Path normalize() {
        final NexusPath current = resolved.get();
        return current != null ? current.normalize() : super.normalize();
    }
}
