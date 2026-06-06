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

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance test for the filesystem attribute cache: the natural
 * {@code root.relativize(file)} → {@code root.resolve(relative)} round-trip returns the right
 * size/modified and issues <em>zero</em> additional HEADs, because listing the directory already
 * told the filesystem everything. Observes real HTTP traffic via the counting {@link Foo}.
 */
public class RoundTripCacheTest {

    private static final String VERSION = "org/apache/tomee/apache-tomee/9.0.1-TT.2";

    private static Foo foo;
    private static HttpServer server;
    private static Path root;

    @BeforeAll
    public static void up() throws Exception {
        foo = new Foo();
        server = HttpServer.start(foo);
        root = Nexus.builder()
                .baseUri(server.getUri().resolve("/content/repositories/releases-tomitribe/"))
                .credentials("snoopy", "woodstock")
                .build();
    }

    @AfterAll
    public static void down() throws Exception {
        server.close();
    }

    @Test
    public void roundTripCostsNoHeads() throws Exception {
        // List the version directory — populates the cache for every child.
        final Path white;
        try (Stream<Path> children = Files.list(root.resolve(VERSION))) {
            white = children.filter(Files::isRegularFile).findFirst().orElseThrow();
        }
        final long size = Files.size(white);
        final Instant modified = Files.getLastModifiedTime(white).toInstant();
        assertTrue(size > 0);

        // Everything above (listing + reading the listing handle's attrs) is done; snapshot now.
        final int headsBefore = foo.heads();

        // The round-trip a caller would naturally write.
        final Path relative = root.relativize(white);
        final Path reconstructed = root.resolve(relative);

        assertEquals(size, Files.size(reconstructed));
        assertEquals(modified, Files.getLastModifiedTime(reconstructed).toInstant());
        assertEquals(white, reconstructed); // same address

        assertEquals(headsBefore, foo.heads(), "relativize → resolve round-trip must not HEAD");
    }
}
