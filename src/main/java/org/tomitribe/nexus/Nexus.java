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

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;

import java.net.URI;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * Entry point — builds a read-only Nexus {@link java.nio.file.FileSystem} and returns
 * its root {@link Path}. The {@code FileSystem} is constructed directly and never
 * registered with the JVM (no {@code META-INF/services}); the returned {@code Path}
 * carries its own provider, so {@code Files.copy} / {@code Files.walk} dispatch through
 * it without global wiring.
 *
 * <p>The root {@code /} maps to the configured base URI and {@code ..} can never climb
 * above it, so an off-base request is unrepresentable — there is no host or scheme to
 * point elsewhere.
 *
 * <p>Usage — the whole crawl-filter-download as vanilla NIO:
 * <pre>{@code
 *   final Path nexus = Nexus.root(
 *           URI.create("https://nexus.example/content/repositories/releases/"),
 *           "user", "pass");
 *
 *   try (Stream<Path> walk = Files.walk(nexus.resolve("org/apache/tomee/apache-tomee/9.0.1"))) {
 *       walk.filter(Files::isRegularFile)
 *           .filter(p -> p.getFileName().toString().endsWith(".zip"))
 *           .forEach(p -> {
 *               try {
 *                   Files.copy(p, local.resolve(p.getFileName().toString()), REPLACE_EXISTING);
 *               } catch (IOException e) {
 *                   throw new UncheckedIOException(e);
 *               }
 *           });
 *   }
 * }</pre>
 */
public final class Nexus {

    private Nexus() {
    }

    /** Anonymous access (e.g. a public repository such as Maven Central). */
    public static Path root(final URI baseUri) {
        return root(baseUri, null, null);
    }

    /**
     * Authenticated access. Credentials are attached as HTTP Basic, and only ever to the
     * configured base host — a defense-in-depth guard so credentials cannot leak to an
     * unexpected target.
     */
    public static Path root(final URI baseUri, final String username, final String password) {
        Objects.requireNonNull(baseUri, "baseUri");

        final HttpClientBuilder builder = HttpClientBuilder.create();
        if (username != null) {
            final String value = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            final HttpRequestInterceptor authorization = (request, context) -> {
                final HttpHost target = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                if (isSameHost(target, baseUri)) {
                    request.addHeader("Authorization", "Basic " + value);
                }
            };
            builder.addInterceptorFirst(authorization);
        }

        final HttpClient client = new HttpClient(builder.build());
        final NexusFileSystemProvider provider = new NexusFileSystemProvider();
        final NexusFileSystem fs = new NexusFileSystem(provider, baseUri, client);
        return fs.root();
    }

    private static boolean isSameHost(final HttpHost target, final URI base) {
        if (target == null) return false;
        if (!target.getHostName().equalsIgnoreCase(base.getHost())) return false;
        final int basePort = base.getPort() == -1 ? defaultPort(base.getScheme()) : base.getPort();
        final int targetPort = target.getPort() == -1 ? defaultPort(target.getSchemeName()) : target.getPort();
        return basePort == targetPort;
    }

    private static int defaultPort(final String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
