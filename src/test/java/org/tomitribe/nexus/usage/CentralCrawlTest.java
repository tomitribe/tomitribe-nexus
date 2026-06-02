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
package org.tomitribe.nexus.usage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tomitribe.nexus.Foo;
import org.tomitribe.nexus.HttpServer;
import org.tomitribe.nexus.Nexus;
import org.tomitribe.util.IO;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CentralCrawlTest {


    private static HttpServer server;
    private static Path root;

    @BeforeAll
    public static void setup() throws Exception {
        server = HttpServer.start(new Foo("central"));
        root = Nexus.builder()
                .baseUri(server.getUri().resolve("/content/repositories/releases-tomitribe/"))
                .credentials("snoopy", "woodstock")
                .build();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        server.close();
    }

    @Test
    public void crawl() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("central-list.txt");
        final String expected = IO.slurp(resource);

        final Path version = root.resolve("org/apache/tomee/apache-tomee/");

        try (var stream = Files.walk(version)) {

            final String result = stream
                    .map(path -> {
                        try {
                            final BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                            final long size = attributes.size();
                            final Instant modified = attributes.lastModifiedTime().toInstant();
                            return String.format("%12d  [%s]  %s", size, modified, path);
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining(System.lineSeparator()));

            assertEquals(expected, result);
        }
    }

}
