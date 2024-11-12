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

import static de.securedimensions.frostserver.plugin.websub.PluginWebSub.TAG_ERROR_ENTITY_NOT_ALLOWED;

import de.fraunhofer.iosb.ilt.frostclient.SensorThingsService;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsPlus;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsV11Sensing;
import de.fraunhofer.iosb.ilt.statests.AbstractTestClass;
import de.fraunhofer.iosb.ilt.statests.ServerVersion;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the W3C Discovery for URLs that have no query segment (no ODATA part)
 * e.g. http://localhost:8080/FROST-Server/v1.1/Observations
 *
 * @author Andreas Matheus
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class DiscoveryPathTests extends AbstractTestClass {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryPathTests.class);
    private static final long serialVersionUID = 1639739965;
    private static final Map<String, String> SERVER_PROPERTIES = new LinkedHashMap<>();

    static {

        SERVER_PROPERTIES.put("persistence.idGenerationMode", "ServerAndClientGenerated");
        SERVER_PROPERTIES.put("plugins.coreModel.enable", "true");
        SERVER_PROPERTIES.put("plugins.coreModel.idType", "LONG");
        SERVER_PROPERTIES.put("mqtt.Enabled", "false");
        SERVER_PROPERTIES.put("mqtt.enabled", "false");
        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.websub.PluginWebSub");
        SERVER_PROPERTIES.put("plugins.websub.enable", "true");
        SERVER_PROPERTIES.put("plugins.websub.hubUrl", "https://websub-hub.citiobs.secd.eu/api/subscriptions");
        SERVER_PROPERTIES.put("plugins.websub.helpUrl", "https://github.com/securedimensions/FROST-Server-WebSub/help.html");
    }

    private static final String[] rootTopicsOthers = {"Foo", "/", "", " ", "#"};
    private static final String[] rootTopicSingle = {"FeaturesOfInterest(1)"};
    private static Map<String, String[]> TEST_DATA = new HashMap<>();

    static {
        // Other test topics
        TEST_DATA.put("Foo", new String[]{"404", null, null});
        TEST_DATA.put("/", new String[]{"200", null, null});
        TEST_DATA.put("", new String[]{"200", null, null});
        TEST_DATA.put(" ", new String[]{"200", null, null});
        TEST_DATA.put("#", new String[]{"200", null, null});
    }

    protected static SensorThingsPlus pMdl;
    protected static SensorThingsService serviceSTAplus;

    public DiscoveryPathTests(ServerVersion version) {
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

            // Create FeatureOfInterest for testing
            createFeatureOfInterest();
        } catch (MalformedURLException ex) {
            LOGGER.error("Failed to create URL", ex);
        } catch (IOException ex) {
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
    public void testDiscoveryByPath() throws IOException {
        for (String entity : "Datastreams,Sensors,Things,Locations,HistoricalLocations,Observations,FeaturesOfInterest,MultiDatastreams".split(",")) {
            testDiscoveryByPath(entity, "GET");
            testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicSingle) {
            testDiscoveryByPath(entity, "GET");
            testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicsOthers) {
            testDiscoveryByPath(entity, "GET");
            testDiscoveryByPath(entity, "HEAD");
        }

    }

    public void createFeatureOfInterest() throws IOException {
        LOGGER.info("createFeatureOfInterest()");
        String request = "{\"@iot.id\":1,\"name\":\"Munich\",\"description\":\"A nice place on Earth\",\"encodingType\":\"application/geo+json\",\"feature\":{\"type\":\"Point\",\"coordinates\":[11.509234,48.110728]}}";
        HttpPost httpPost = new HttpPost(serverSettings.getServiceUrl(version) + "/FeaturesOfInterest");
        HttpEntity stringEntity = new StringEntity(request, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        try (CloseableHttpResponse response = serviceSTAplus.execute(httpPost)) {

            if (response.getStatusLine().getStatusCode() == 201) {
                Assertions.assertTrue(true, "FeatureOfInterest created");
            } else {
                fail(response, "FeatureOfInterest creation failed");
            }
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

    private void testDiscoveryByPath(String rootTopic, String method) throws IOException {
        LOGGER.info("  testDiscoveryByPath " + method + " (%s)".formatted(rootTopic));

        String url = serverSettings.getServiceUrl(version) + "/" + rootTopic;

        HttpRequestBase http = null;
        if (method.equalsIgnoreCase("GET"))
            http = new HttpGet(url.trim());
        else
            http = new HttpHead(url.trim());

        try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
            int expectedStatusCode = Integer.parseInt(TEST_DATA.get(rootTopic)[0]);

            Map<String, String> linkHeaders = getLinkHeaders(response.getHeaders("Link"));
            String hubLink = linkHeaders.get("hub");
            Assertions.assertTrue(hubLink != null, "hub not null");
            Assertions.assertTrue(SERVER_PROPERTIES.get("plugins.websub.hubUrl").equalsIgnoreCase(hubLink), "hub match");
            String selfLink = linkHeaders.get("self");
            String helpLink = linkHeaders.get("help");
            Assertions.assertTrue(response.getStatusLine().getStatusCode() == expectedStatusCode, "response status code for method=" + method + " url=" + url + ": actual=" + response.getStatusLine().getStatusCode() + " expected=" + expectedStatusCode);
            if (TEST_DATA.get(rootTopic)[1] == null) {
                Assertions.assertTrue(selfLink == null, "requested entityset is not in rootTopic => there is no Link rel=self");
            } else {
                String expectedSelfLink = serverSettings.getServiceUrl(version) + "/" + TEST_DATA.get(rootTopic)[1];
                Assertions.assertTrue(selfLink.equalsIgnoreCase(expectedSelfLink), "self-link match");
            }
            if (TEST_DATA.get(rootTopic)[2] != null) {
                // Testing help-Link
                String expectedHelpLink = SERVER_PROPERTIES.get("plugins.websub.helpUrl") + "#" + TEST_DATA.get(rootTopic)[2];
                Assertions.assertTrue(helpLink.equalsIgnoreCase(expectedHelpLink), "help-link match");
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

    public static class DiscoveryPathTestsAll extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams,Sensors,Things,Locations,HistoricalLocations,Observations,FeaturesOfInterest,MultiDatastreams,Parties,Licenses,Campaigns,ObservationGroups,Relations");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null});
            TEST_DATA.put("Sensors", new String[]{"200", "Sensors", null});
            TEST_DATA.put("Things", new String[]{"200", "Things", null});
            TEST_DATA.put("Locations", new String[]{"200", "Locations", null});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", "HistoricalLocations", null});
            TEST_DATA.put("Observations", new String[]{"200", "Observations", null});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", "FeaturesOfInterest", null});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", "MultiDatastreams", null});
            // Single Entity
            TEST_DATA.put("FeaturesOfInterest(1)", new String[]{"200", "FeaturesOfInterest(1)", null});

        }

        public DiscoveryPathTestsAll() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD0 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null});
            // Single Entity
            TEST_DATA.put("FeaturesOfInterest(1)", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
        }

        public DiscoveryPathTestMD0() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD1 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", "MultiDatastreams", null});
            // Single Entity
            TEST_DATA.put("FeaturesOfInterest(1)", new String[]{"200", null, TAG_ERROR_ENTITY_NOT_ALLOWED});
        }

        public DiscoveryPathTestMD1() {
            super(ServerVersion.v_1_1);
        }
    }

}
