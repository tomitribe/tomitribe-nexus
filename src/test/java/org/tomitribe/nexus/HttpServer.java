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

import jakarta.servlet.http.HttpServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.net.URI;
import java.nio.file.Files;

/**
 * Minimal embedded-Tomcat test server: registers a single servlet at {@code /*} on an
 * ephemeral port. Plain servlet container — no JAX-RS, no framework.
 */
public final class HttpServer implements AutoCloseable {

    private final URI uri;
    private final Tomcat tomcat;

    private HttpServer(final URI uri, final Tomcat tomcat) {
        this.uri = uri;
        this.tomcat = tomcat;
    }

    public URI getUri() {
        return uri;
    }

    public static HttpServer start(final HttpServlet servlet) throws Exception {
        final String base = Files.createTempDirectory("tomitribe-nexus-tomcat").toString();

        final Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base);
        tomcat.setPort(0); // ephemeral
        tomcat.getConnector();

        final Context context = tomcat.addContext("", base);
        Tomcat.addServlet(context, "fixtures", servlet);
        context.addServletMappingDecoded("/*", "fixtures");

        tomcat.start();

        final int port = tomcat.getConnector().getLocalPort();
        return new HttpServer(URI.create("http://localhost:" + port), tomcat);
    }

    @Override
    public void close() throws Exception {
        tomcat.stop();
        tomcat.destroy();
    }
}
