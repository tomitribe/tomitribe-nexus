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
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic transport for the per-state tests — no server. Classifies by path:
 * a path containing {@code missing} is a 404; one ending {@code .jar}/{@code .pom}/{@code .txt}
 * is a file (5-byte body, {@code application/octet-stream}); anything else is a directory
 * (an HTML listing, {@code text/html}). HEAD and GET calls are counted so a test can prove
 * resolution happens at most once.
 */
final class StubHttpClient extends HttpClient {

    static final String LISTING = ""
            + "<html><body>\n"
            + "<a href=\"../\">../</a>\n"
            + "<a href=\"child-dir/\">child-dir/</a>\n"
            + "<a href=\"child-file.jar\">child-file.jar</a>\n"
            + "</body></html>";

    static final byte[] FILE_BODY = "hello".getBytes(StandardCharsets.UTF_8);

    final AtomicInteger heads = new AtomicInteger();
    final AtomicInteger gets = new AtomicInteger();

    StubHttpClient() {
        super(null);
    }

    @Override
    public HttpResponse head(final URI uri) {
        heads.incrementAndGet();
        return classify(uri, false);
    }

    @Override
    public HttpResponse get(final URI uri) {
        gets.incrementAndGet();
        return classify(uri, true);
    }

    private HttpResponse classify(final URI uri, final boolean withBody) {
        final String path = uri.getPath();
        if (path.contains("missing")) {
            return response(404, null, null);
        }
        if (path.endsWith(".jar") || path.endsWith(".pom") || path.endsWith(".txt")) {
            return response(200, "application/octet-stream", withBody ? FILE_BODY : null);
        }
        return response(200, "text/html", withBody ? LISTING.getBytes(StandardCharsets.UTF_8) : null);
    }

    private HttpResponse response(final int status, final String contentType, final byte[] body) {
        final BasicHttpResponse response =
                new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "reason"));
        if (contentType != null) {
            response.setHeader("Content-Type", contentType);
        }
        if ("application/octet-stream".equals(contentType)) {
            response.setHeader("Content-Length", String.valueOf(FILE_BODY.length));
        }
        if (body != null) {
            response.setEntity(new ByteArrayEntity(body));
        }
        return response;
    }
}
