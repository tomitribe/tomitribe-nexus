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

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.tomitribe.nexus.parse.CentralParser;
import org.tomitribe.nexus.parse.Nexus2Parser;
import org.tomitribe.nexus.parse.Parser;
import org.tomitribe.nexus.parse.Parsers;
import org.tomitribe.util.IO;
import org.tomitribe.util.Join;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParsingTest {

    @Test
    public void nexus2() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("fixtures/apache-tomee_");
        final String slurp = IO.slurp(resource);

        final Parser parser = new Nexus2Parser();

        assertTrue(parser.test(slurp));
        assertFalse(new CentralParser().test(slurp));

        final List<Parser.Node> nodes = parser.parse(slurp);

        assertEquals("Node{name='7.0.1-SP.1/', modified=2022-08-27T23:19:38Z, size=null}\n" +
                "Node{name='7.0.1-SP.5/', modified=2022-08-27T23:19:49Z, size=null}\n" +
                "Node{name='7.0.10-TT.18/', modified=2023-03-03T19:37:07Z, size=null}\n" +
                "Node{name='7.0.10-TT.7/', modified=2022-08-27T23:20:25Z, size=null}\n" +
                "Node{name='8.0.9-TT.7/', modified=2022-08-27T23:28:05Z, size=null}\n" +
                "Node{name='8.0.9-TT.8/', modified=2022-08-27T23:28:13Z, size=null}\n" +
                "Node{name='9.0.1-TT.1/', modified=2023-03-16T22:36:02Z, size=null}\n" +
                "Node{name='9.0.1-TT.2/', modified=2023-03-24T00:30:42Z, size=null}\n" +
                "Node{name='maven-metadata.xml', modified=2023-04-06T20:24:49Z, size=5203}\n" +
                "Node{name='maven-metadata.xml.md5', modified=2023-04-06T20:24:49Z, size=32}\n" +
                "Node{name='maven-metadata.xml.sha1', modified=2023-04-06T20:24:49Z, size=40}\n" +
                "Node{name='maven-metadata.xml.sha256', modified=2023-04-06T20:24:49Z, size=64}\n" +
                "Node{name='maven-metadata.xml.sha512', modified=2023-04-06T20:24:49Z, size=128}", Join.join("\n", nodes));
    }

//    @Test
    public void fetch() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("central/apache-tomee_");
        final String slurp = IO.slurp(resource);
        final Parser parser = new CentralParser();

        final List<Parser.Node> nodes = parser.parse(slurp);
        final File central = new File("/Users/dblevins/work/tomitribe/tomitribe-nexus/src/test/resources/central");


        for (final Parser.Node node : nodes) {

            try (final HttpClient client = new HttpClient(HttpClientBuilder.create().build())){
                System.out.println(node);
                final HttpResponse response = client.get(URI.create("https://repo1.maven.org/maven2/org/apache/tomee/apache-tomee/" + node.getName()));
                final String content = IO.slurp(response.getEntity().getContent());
                final File file = new File(central, "apache-tomee_" + node.getName().replace('/', '_'));
                IO.copy(content, file);

            }
        }
    }

    @Test
    public void central() throws Exception {
        final URL resource = this.getClass().getClassLoader().getResource("central/apache-tomee_");
        final String slurp = IO.slurp(resource);

        final Parser parser = new CentralParser();

        assertTrue(parser.test(slurp));
        assertFalse(new Nexus2Parser().test(slurp));

        final List<Parser.Node> nodes = parser.parse(slurp);

        assertEquals("Node{name='10.0.0/', modified=2024-12-16T07:47:00Z, size=null}\n" +
                "Node{name='10.0.0-M1/', modified=2024-04-02T07:26:00Z, size=null}\n" +
                "Node{name='10.0.0-M2/', modified=2024-07-16T07:51:00Z, size=null}\n" +
                "Node{name='10.0.0-M3/', modified=2024-10-05T15:24:00Z, size=null}\n" +
                "Node{name='10.0.1/', modified=2025-03-20T13:17:00Z, size=null}\n" +
                "Node{name='10.1.0/', modified=2025-06-17T05:07:00Z, size=null}\n" +
                "Node{name='10.1.1/', modified=2025-08-16T12:18:00Z, size=null}\n" +
                "Node{name='10.1.2/', modified=2025-09-16T13:06:00Z, size=null}\n" +
                "Node{name='10.1.3/', modified=2025-12-07T15:26:00Z, size=null}\n" +
                "Node{name='10.1.4/', modified=2026-01-29T20:33:00Z, size=null}\n" +
                "Node{name='10.1.5/', modified=2026-04-26T06:47:00Z, size=null}\n" +
                "Node{name='7.0.0/', modified=2016-05-17T21:42:00Z, size=null}\n" +
                "Node{name='7.0.0-M1/', modified=2015-12-05T23:54:00Z, size=null}\n" +
                "Node{name='7.0.0-M2/', modified=2016-02-23T09:37:00Z, size=null}\n" +
                "Node{name='7.0.0-M3/', modified=2016-03-03T15:48:00Z, size=null}\n" +
                "Node{name='7.0.1/', modified=2016-06-22T22:28:00Z, size=null}\n" +
                "Node{name='7.0.2/', modified=2016-11-06T18:40:00Z, size=null}\n" +
                "Node{name='7.0.3/', modified=2017-03-07T21:24:00Z, size=null}\n" +
                "Node{name='7.0.4/', modified=2017-09-26T19:20:00Z, size=null}\n" +
                "Node{name='7.0.5/', modified=2018-07-10T11:42:00Z, size=null}\n" +
                "Node{name='7.0.6/', modified=2019-06-06T09:12:00Z, size=null}\n" +
                "Node{name='7.0.7/', modified=2020-01-07T13:23:00Z, size=null}\n" +
                "Node{name='7.0.8/', modified=2020-05-19T13:26:00Z, size=null}\n" +
                "Node{name='7.0.9/', modified=2020-08-05T15:45:00Z, size=null}\n" +
                "Node{name='7.1.0/', modified=2018-09-02T21:00:00Z, size=null}\n" +
                "Node{name='7.1.1/', modified=2019-06-05T21:26:00Z, size=null}\n" +
                "Node{name='7.1.2/', modified=2020-01-07T12:05:00Z, size=null}\n" +
                "Node{name='7.1.3/', modified=2020-05-19T11:52:00Z, size=null}\n" +
                "Node{name='7.1.4/', modified=2020-08-05T15:06:00Z, size=null}\n" +
                "Node{name='8.0.0/', modified=2019-09-13T11:31:00Z, size=null}\n" +
                "Node{name='8.0.0-M1/', modified=2018-10-13T23:56:00Z, size=null}\n" +
                "Node{name='8.0.0-M2/', modified=2019-01-25T22:34:00Z, size=null}\n" +
                "Node{name='8.0.0-M3/', modified=2019-05-23T07:24:00Z, size=null}\n" +
                "Node{name='8.0.1/', modified=2020-01-07T14:54:00Z, size=null}\n" +
                "Node{name='8.0.10/', modified=2022-02-10T14:54:00Z, size=null}\n" +
                "Node{name='8.0.11/', modified=2022-04-13T15:06:00Z, size=null}\n" +
                "Node{name='8.0.12/', modified=2022-06-07T08:11:00Z, size=null}\n" +
                "Node{name='8.0.13/', modified=2022-10-11T11:14:00Z, size=null}\n" +
                "Node{name='8.0.14/', modified=2023-01-17T12:55:00Z, size=null}\n" +
                "Node{name='8.0.15/', modified=2023-05-08T12:36:00Z, size=null}\n" +
                "Node{name='8.0.16/', modified=2023-10-29T17:28:00Z, size=null}\n" +
                "Node{name='8.0.2/', modified=2020-05-14T11:01:00Z, size=null}\n" +
                "Node{name='8.0.3/', modified=2020-06-19T10:58:00Z, size=null}\n" +
                "Node{name='8.0.4/', modified=2020-07-22T11:28:00Z, size=null}\n" +
                "Node{name='8.0.5/', modified=2020-11-17T20:23:00Z, size=null}\n" +
                "Node{name='8.0.6/', modified=2021-01-14T14:31:00Z, size=null}\n" +
                "Node{name='8.0.7/', modified=2021-05-03T12:28:00Z, size=null}\n" +
                "Node{name='8.0.8/', modified=2021-08-31T14:51:00Z, size=null}\n" +
                "Node{name='8.0.9/', modified=2021-12-23T11:46:00Z, size=null}\n" +
                "Node{name='9.0.0/', modified=2023-01-03T08:44:00Z, size=null}\n" +
                "Node{name='9.0.0-M7/', modified=2021-05-03T14:44:00Z, size=null}\n" +
                "Node{name='9.0.0-M8/', modified=2022-06-28T08:06:00Z, size=null}\n" +
                "Node{name='9.0.0.RC1/', modified=2022-10-31T09:16:00Z, size=null}\n" +
                "Node{name='9.1.0/', modified=2023-06-06T10:03:00Z, size=null}\n" +
                "Node{name='9.1.1/', modified=2023-10-12T11:17:00Z, size=null}\n" +
                "Node{name='9.1.2/', modified=2023-12-12T16:13:00Z, size=null}\n" +
                "Node{name='9.1.3/', modified=2024-04-08T09:18:00Z, size=null}\n" +
                "Node{name='maven-metadata.xml', modified=2026-05-05T06:29:00Z, size=2128}\n" +
                "Node{name='maven-metadata.xml.md5', modified=2026-05-05T06:29:00Z, size=32}\n" +
                "Node{name='maven-metadata.xml.sha1', modified=2026-05-05T06:29:00Z, size=40}\n" +
                "Node{name='maven-metadata.xml.sha256', modified=2026-05-05T06:29:00Z, size=64}\n" +
                "Node{name='maven-metadata.xml.sha512', modified=2026-05-05T06:29:00Z, size=128}", Join.join("\n", nodes));
    }

    @Test
    public void testSelection() throws Exception {
        final ClassLoader loader = this.getClass().getClassLoader();
        final String central = IO.slurp(loader.getResource("central/apache-tomee_"));
        final String nexus2 = IO.slurp(loader.getResource("fixtures/apache-tomee_"));

        assertEquals(62, Parsers.parse(central).size());
        assertEquals(13, Parsers.parse(nexus2).size());
    }
}
