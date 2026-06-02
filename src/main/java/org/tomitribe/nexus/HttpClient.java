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

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

/**
 * The narrow HTTP surface the filesystem needs: GET, HEAD, and a content-length probe.
 * Isolated so the transport can be swapped (e.g. to {@code java.net.http}) without
 * touching the path lattice.
 */
class HttpClient implements Closeable {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.13) Gecko/20101206 Ubuntu/10.10 (maverick) Firefox/3.6.13";

    private final CloseableHttpClient client;

    public HttpClient(final CloseableHttpClient client) {
        this.client = client;
    }

    public long getContentLength(final URI uri) throws IOException {
        final HttpResponse head = head(uri);
        for (final Header header : head.getHeaders("Content-Length")) {
            return Long.parseLong(header.getValue());
        }
        return -1;
    }

    public HttpResponse get(final URI uri) throws IOException {
        final HttpGet request = new HttpGet(uri);
        request.setHeader("User-Agent", USER_AGENT);
        return client.execute(request);
    }

    public HttpResponse head(final URI uri) throws IOException {
        final HttpHead request = new HttpHead(uri);
        request.setHeader("User-Agent", USER_AGENT);
        return client.execute(request);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
