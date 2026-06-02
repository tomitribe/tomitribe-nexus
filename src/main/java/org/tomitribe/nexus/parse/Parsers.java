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
package org.tomitribe.nexus.parse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Parsers {

    private Parsers() {
    }

    private static final List<Parser> parsers = new ArrayList<>();

    static {
        parsers.add(new Nexus2Parser());
        parsers.add(new CentralParser());
    }

    public static List<Parser.Node> parse(final String content) throws IOException {
        for (final Parser parser : parsers) {
            if (parser.test(content)) {
                return parser.parse(content);
            }
        }
        return Collections.EMPTY_LIST;
    }

}
