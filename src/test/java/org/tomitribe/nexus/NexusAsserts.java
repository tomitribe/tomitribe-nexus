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
 * Observe a path's state through the public API only — no privileged access. {@code normalize()}
 * returns the current concrete state (an unresolved path until it has been resolved), so its
 * runtime class name is the state. Defined once so the trick can be refined in a single place
 * rather than copied across every assertion.
 *
 * <p>Built on the public {@link Path} surface, so the black-box {@code usage} tests — which can't
 * see the package-private state types at all — use it exactly as the in-package tests do.
 */
public final class NexusAsserts {

    private NexusAsserts() {
    }

    public static void assertType(final Path path, final String expected) {
        assertEquals(expected, typeOf(path), "Expected state " + expected + " for " + path);
    }

    public static String typeOf(final Path path) {
        final String simpleName = path.normalize().getClass().getSimpleName();
        // The unresolved state is the NexusUnknown class itself; once resolved, normalize()
        // returns the concrete delegate, so any other class name is a settled state.
        return simpleName.equals("NexusUnknown") ? "unresolved" : simpleName;
    }
}
