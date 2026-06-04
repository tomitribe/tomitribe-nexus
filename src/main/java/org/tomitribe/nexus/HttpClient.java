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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The narrow HTTP surface the filesystem needs: a streaming GET and a HEAD.
 * Isolated so the transport can be swapped (e.g. to {@code java.net.http}) without
 * touching the path lattice.
 *
 * <p>Connection discipline matters: Apache HttpClient pools a small number of connections
 * per route (2 by default) and only returns one to the pool when its response is fully
 * consumed or closed. {@link #head(URI)} captures the response headers and closes the
 * response itself, so a HEAD never leaks a connection. {@link #get(URI)} streams, so the
 * caller owns the returned response and must close it (closing the entity stream releases
 * the connection) — and must consume it on the error path too.
 */
class HttpClient implements Closeable {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.13) Gecko/20101206 Ubuntu/10.10 (maverick) Firefox/3.6.13";

    private final CloseableHttpClient client;

    public HttpClient(final CloseableHttpClient client) {
        this.client = client;
    }

    public CloseableHttpResponse get(final URI uri) throws IOException {
        final HttpGet request = new HttpGet(uri);
        request.setHeader("User-Agent", USER_AGENT);
        return client.execute(request);
    }

    /**
     * Issues a HEAD and returns its status and headers, releasing the connection before
     * returning — so no caller can leak it. All headers are captured because the server
     * type (e.g. {@code Server: Nexus/2.x}) changes how a response is interpreted.
     */
    public Head head(final URI uri) throws IOException {
        final HttpHead request = new HttpHead(uri);
        request.setHeader("User-Agent", USER_AGENT);
        try (final CloseableHttpResponse response = client.execute(request)) {
            final Map<String, String> headers = new HashMap<>();
            for (final Header header : response.getAllHeaders()) {
                headers.put(header.getName().toLowerCase(Locale.ROOT), header.getValue());
            }
            return new Head(response.getStatusLine().getStatusCode(), headers);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    /** A HEAD's status and headers (lower-cased names); the connection is already released. */
    record Head(int status, Map<String, String> headers) {

        String header(final String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        String contentType() {
            return header("Content-Type");
        }

        String server() {
            return header("Server");
        }

        Long contentLength() {
            final String value = header("Content-Length");
            return value == null ? null : Long.parseLong(value);
        }

        Instant lastModified() {
            final String value = header("Last-Modified");
            if (value == null) return null;
            final Date date = DateUtils.parseDate(value);
            return date == null ? null : date.toInstant();
        }

        boolean isHtml() {
            final String contentType = contentType();
            return contentType != null && contentType.contains("text/html");
        }

        boolean isNexus2() {
            final String server = server();
            return server != null && server.startsWith("Nexus/2");
        }
    }
}
