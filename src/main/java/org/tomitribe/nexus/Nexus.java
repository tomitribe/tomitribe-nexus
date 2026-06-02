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
 * Entry point — configure with a {@link #builder()} and {@code build()} a read-only
 * {@link java.nio.file.Path} rooted at the repository.
 *
 * <pre>{@code
 *   Path root = Nexus.builder()
 *           .baseUri(URI.create("https://nexus.example/content/repositories/releases/"))
 *           .credentials("user", "pass")   // optional; omit for anonymous access
 *           .build();
 *
 *   try (Stream<Path> walk = Files.walk(root.resolve("org/apache/tomee/apache-tomee/9.0.1"))) {
 *       walk.filter(Files::isRegularFile)
 *           .forEach(p -> ...);            // Files.copy(p, local.resolve(p.getFileName().toString()), ...)
 *   }
 * }</pre>
 *
 * <p>The returned {@code Path} carries its own provider, so {@code Files.copy}/{@code Files.walk}
 * dispatch through it with no JVM registration. The root {@code /} is the base URI and {@code ..}
 * cannot climb above it, so an off-base request is unrepresentable.
 *
 * <p>A builder rather than a constructor on purpose: new options can be added over time without
 * a growing pile of overloads to maintain.
 */
public final class Nexus {

    private Nexus() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI baseUri;
        private String username;
        private String password;

        private Builder() {
        }

        public Builder baseUri(final URI baseUri) {
            this.baseUri = baseUri;
            return this;
        }

        public Builder baseUri(final String baseUri) {
            return baseUri(URI.create(baseUri));
        }

        /** Optional. When omitted, no authentication header is ever sent. */
        public Builder credentials(final String username, final String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Path build() {
            Objects.requireNonNull(baseUri, "baseUri is required");

            final HttpClientBuilder builder = HttpClientBuilder.create();
            if (username != null) {
                final String value = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                final HttpRequestInterceptor authorization = (request, context) -> {
                    // Attach credentials only to the configured base host — never leak them elsewhere.
                    final HttpHost target = (HttpHost) context.getAttribute(HttpClientContext.HTTP_TARGET_HOST);
                    if (isSameHost(target, baseUri)) {
                        request.addHeader("Authorization", "Basic " + value);
                    }
                };
                builder.addInterceptorFirst(authorization);
            }

            final HttpClient client = new HttpClient(builder.build());
            final NexusFileSystem fs = new NexusFileSystem(new NexusFileSystemProvider(), baseUri, client);
            return fs.root();
        }
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
