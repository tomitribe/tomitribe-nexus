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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class CentralParser implements Parser {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId UTC = ZoneId.of("UTC");
    public static final Pattern ENTRY = Pattern.compile(
            "<a\\s+href=\"[^\"]*\"\\s+title=\"[^\"]*\">(?<name>[^<]+)</a>\\s+" +
                    "(?<dateTime>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\s+" +
                    "(?<size>-|\\d+)\\s*"
    );


    @Override
    public boolean test(final String content) {
        final String[] split = content.split("\r?\n");
        return Stream.of(split)
                .anyMatch(s -> ENTRY.matcher(s).matches());
    }

    public List<Node> parse(final String content) throws IOException {
        final List<Node> nodes = new ArrayList<>();

        for (final String line : content.split("\r?\n")) {
            final Matcher matcher = ENTRY.matcher(line);

            if (matcher.matches()) {

                final String name = matcher.group("name");
                final Instant modified = parseInstant(matcher.group("dateTime"));
                final Long size = parseSize(matcher.group("size"));

                nodes.add(new Node(name, modified, size));
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

    private static Instant parseInstant(final String value) {

        return LocalDateTime.parse(value, DATE_TIME_FORMAT)
                .atZone(UTC)
                .toInstant();
    }
}
