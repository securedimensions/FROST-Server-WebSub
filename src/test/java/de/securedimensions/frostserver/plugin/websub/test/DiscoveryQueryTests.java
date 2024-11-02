/*
 * Copyright (C) 2024 Secure Dimensions GmbH, D-81377
 * Munich, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.securedimensions.frostserver.plugin.websub.test;

import static de.securedimensions.frostserver.plugin.websub.PluginWebSub.TAG_ERROR_ODATA_EXPAND_DISABLED;
import static de.securedimensions.frostserver.plugin.websub.PluginWebSub.TAG_ERROR_ODATA_FILTER_DISABLED;

import de.fraunhofer.iosb.ilt.frostclient.SensorThingsService;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsPlus;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsV11Sensing;
import de.fraunhofer.iosb.ilt.statests.AbstractTestClass;
import de.fraunhofer.iosb.ilt.statests.ServerVersion;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the W3C Discovery for URLs that have a query segment (an ODATA part)
 * e.g. http://localhost:8080/FROST-Server/v1.1/Observations?$select=result
 *
 * @author Andreas Matheus
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class DiscoveryQueryTests extends AbstractTestClass {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryQueryTests.class);
    private static final long serialVersionUID = 1639739965;
    private static final Map<String, String> SERVER_PROPERTIES = new LinkedHashMap<>();
    static {
        SERVER_PROPERTIES.put("mqtt.Enabled", "false");
        SERVER_PROPERTIES.put("mqtt.enabled", "false");
        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.websub.PluginWebSub");
        SERVER_PROPERTIES.put("plugins.websub.enable", "true");
        SERVER_PROPERTIES.put("plugins.websub.hubUrl", "https://websub-hub.citiobs.secd.eu/api/subscriptions");
        SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Observations");
        SERVER_PROPERTIES.put("plugins.websub.errorUrl", "https://github.com/securedimensions/FROST-Server-WebSub/errors");
        SERVER_PROPERTIES.put("plugins.websub.errorRel", "http://www.opengis.net/doc/is/sensorthings-websub/1.0");
        SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
        SERVER_PROPERTIES.put("plugins.staplus.enable", "false");
    }

    private static Map<String, String[]> TEST_DATA = new HashMap<>();
    static {
        // ODATA commands that are not restricted
        TEST_DATA.put("Observations?" + URLEncoder.encode("$top=1"), new String[]{"200", "Observations?$top=1", null, null});
        TEST_DATA.put("Observations?" + URLEncoder.encode("$select=result"), new String[]{"200", "Observations?$select=result", null, null});
        TEST_DATA.put("Observations?" + URLEncoder.encode("$select=result,phenomenonTime"), new String[]{"200", "Observations?$select=result%2CphenomenonTime", null, null});
        TEST_DATA.put("Observations?" + URLEncoder.encode("$orderBy=phenomenonTime asc"), new String[]{"200", "Observations?$orderBy=phenomenonTime%20asc", null, null});
        TEST_DATA.put("Observations?" + URLEncoder.encode("$select=result,phenomenonTime&$orderBy=phenomenonTime asc"), new String[]{"200", "Observations?$select=result%2CphenomenonTime&$orderBy=phenomenonTime%20asc", null, null});
    }
    protected static SensorThingsPlus pMdl;
    protected static SensorThingsService serviceSTAplus;

    public DiscoveryQueryTests(ServerVersion version) {
        super(version, SERVER_PROPERTIES);
    }

    @AfterAll
    public static void tearDown() {
        LOGGER.info("Tearing down.");
    }

    @Override
    protected void setUpVersion() {
        LOGGER.info("Setting up for version {}.", version.urlPart);
        try {
            sMdl = new SensorThingsV11Sensing();
            pMdl = new SensorThingsPlus();
            serviceSTAplus = new SensorThingsService(sMdl, pMdl).setBaseUrl(new URL(serverSettings.getServiceUrl(version))).init();
        } catch (MalformedURLException ex) {
            LOGGER.error("Failed to create URL", ex);
        }
    }

    @Override
    protected void tearDownVersion() {
        LOGGER.info("tearing down");
    }

    /*
     * Success: self Link is not returned or equal to request URL
     */
    @Test
    public void testDiscoveryWithQuery() throws IOException {
        for (String key : TEST_DATA.keySet()) {
            testDiscoveryWithQuery(key, "GET");
            //testDiscoveryWithQuery(key, "HEAD");
        }
    }

    public Map<String, String> getLinkHeaders(Header links[]) {
        if (links == null)
            return new HashMap<>(0);

        Map<String, String> result = new HashMap<>(2);

        for (Header link : links) {
            for (HeaderElement element : link.getElements()) {
                Matcher matcher = Pattern.compile("<(.*)>; .*rel=(.*)").matcher(element.toString());
                if (matcher.find())
                    result.put(matcher.group(2), matcher.group(1));

            }
        }
        return result;
    }

    private void testDiscoveryWithQuery(String key, String method) throws IOException {
        LOGGER.info("  testDiscoveryByPath " + method + " (%s)".formatted(key));

        String url = serverSettings.getServiceUrl(version) + "/" + key;

        HttpRequestBase http = null;
        if (method.equalsIgnoreCase("GET"))
            http = new HttpGet(url);
        else
            http = new HttpHead(url);

        try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
            int expectedStatusCode = Integer.parseInt(TEST_DATA.get(key)[0]);

            Map<String, String> linkHeaders = getLinkHeaders(response.getHeaders("Link"));
            String hubLink = linkHeaders.get("hub");
            Assertions.assertTrue(hubLink != null, "hub not null");
            Assertions.assertTrue(SERVER_PROPERTIES.get("plugins.websub.hubUrl").equalsIgnoreCase(hubLink), "hub match");
            String selfLink = linkHeaders.get("self");
            String errorLink = linkHeaders.get(SERVER_PROPERTIES.get("plugins.websub.errorRel"));
            Assertions.assertTrue(response.getStatusLine().getStatusCode() == expectedStatusCode, "response status code match");
            if (TEST_DATA.get(key)[1] == null) {
                // No self-link
                Assertions.assertTrue(selfLink == null, "requested entityset is not in rootTopic => there is no Link rel=self");
            } else {
                // Testing self-link
                String expectedSelfLink = URLDecoder.decode(url).replaceAll(",", "%2C").replaceAll(" ", "%20");
                Assertions.assertTrue(selfLink.equalsIgnoreCase(expectedSelfLink), "self-link match");
            }
            if (TEST_DATA.get(key)[2] != null) {
                // Testing error-Link
                String expectedErrorLink = SERVER_PROPERTIES.get("plugins.websub.errorUrl") + TEST_DATA.get(key)[3];
                Assertions.assertTrue(errorLink.equalsIgnoreCase(expectedErrorLink), "error-link match");
            }
        }
    }

    private void fail(CloseableHttpResponse response, String assertion) throws ParseException, IOException {
        HttpEntity entity = response.getEntity();
        String msg = "";
        if (entity != null) {
            msg = org.apache.http.util.EntityUtils.toString(entity);
        }

        Assertions.fail(assertion, new Throwable(msg));
    }

    public static class DiscoveryWithQuery00 extends DiscoveryQueryTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "false");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "false");

            TEST_DATA.put("Observations?" + URLEncoder.encode("$filter=result gt 30"), new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ODATA_FILTER_DISABLED});
            TEST_DATA.put("Observations?" + URLEncoder.encode("$expand=Datastream"), new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ODATA_EXPAND_DISABLED});
        }

        public DiscoveryWithQuery00() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryWithQuery01 extends DiscoveryQueryTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "false");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "true");

            TEST_DATA.put("Observations?" + URLEncoder.encode("$filter=result gt 30"), new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ODATA_FILTER_DISABLED});
            TEST_DATA.put("Observations?" + URLEncoder.encode("$expand=Datastream"), new String[]{"200", "Observations?$expand=Datastream", null, null});
        }

        public DiscoveryWithQuery01() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryWithQuery10 extends DiscoveryQueryTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "false");

            TEST_DATA.put("Observations?" + URLEncoder.encode("$filter=result gt 30"), new String[]{"200", "Observations?$filter=result%20gt%2030", null, null});
            TEST_DATA.put("Observations?" + URLEncoder.encode("$expand=Datastream"), new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ODATA_EXPAND_DISABLED});
        }

        public DiscoveryWithQuery10() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryWithQuery11 extends DiscoveryQueryTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "true");

            TEST_DATA.put("Observations?" + URLEncoder.encode("$filter=result gt 30"), new String[]{"200", "Observations?$filter=result%20gt%2030", null, null});
            TEST_DATA.put("Observations?" + URLEncoder.encode("$expand=Datastream"), new String[]{"200", "Observations?$expand=Datastream", null, null});
        }

        public DiscoveryWithQuery11() {
            super(ServerVersion.v_1_1);
        }
    }
}
