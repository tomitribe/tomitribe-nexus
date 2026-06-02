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

import org.tomitribe.swizzle.stream.StreamLexer;
import org.tomitribe.util.IO;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Nexus2Parser implements Parser {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);

    @Override
    public boolean test(final String content) {
        try {
            final StreamLexer lexer = new StreamLexer(IO.read(content));
            return lexer.readAndMark("<tr>", "</tr>") &&
                    lexer.read("<th align=\"left\">Name</th>") != null &&
                    lexer.read("<th>Last Modified</th>") != null &&
                    lexer.read("<th>Size</th>") != null;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Node> parse(final String slurp) throws IOException {
        final List<Node> nodes = new ArrayList<>();
        final StreamLexer lexer = new StreamLexer(IO.read(slurp));

        while (lexer.readAndMark("<tr>", "</tr>")) {
            try {
                final String link = lexer.read("href=\"", "\"");
                final String name = lexer.read(">", "<");

                if (link == null) continue;
                if (name == null) continue;
                if (name.equals("../") || link.equals("../")) continue;

                final Instant modified = parseInstant(lexer.read("<td>", "</td>"));
                final Long size = parseSize(lexer.read("<td align=\"right\">", "</td>"));

                nodes.add(new Node(name, modified, size));
            } finally {
                lexer.unmark();
            }
        }

        return nodes;
    }

    private Long parseSize(final String value) {
        if (value == null) return null;
        final String trimmed = value.trim();
        if (!trimmed.matches("^[0-9]+$")) return null;
        return Long.parseLong(trimmed);
    }

    public static Instant parseInstant(final String value) {
        if (value == null) return null;
        return ZonedDateTime.parse(value, FORMATTER).toInstant();
    }
}
