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

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirror of jaws' {@code Asserts.assertType} — observe which state a path is in,
 * so a test can assert the {@link NexusUnknown} transition before and after the
 * operation that triggers it.
 */
public final class NexusAsserts {

    private NexusAsserts() {
    }

    public static void assertType(final Path path, final String expected) {
        final String actual = ((NexusPath) path).state();
        assertEquals(expected, actual, "Expected state " + expected + " for " + path + ", found " + actual);
    }
}
