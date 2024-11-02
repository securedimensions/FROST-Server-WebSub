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

import de.fraunhofer.iosb.ilt.frostclient.SensorThingsService;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsPlus;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsV11Sensing;
import de.fraunhofer.iosb.ilt.statests.AbstractTestClass;
import de.fraunhofer.iosb.ilt.statests.ServerVersion;
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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.securedimensions.frostserver.plugin.websub.PluginWebSub.*;

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
        SERVER_PROPERTIES.put("mqtt.Enabled", "false");
        SERVER_PROPERTIES.put("mqtt.enabled", "false");
        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.websub.PluginWebSub,de.securedimensions.frostserver.plugin.staplus.PluginPLUS");
        SERVER_PROPERTIES.put("plugins.websub.enable", "true");
        SERVER_PROPERTIES.put("plugins.websub.hubUrl", "https://websub-hub.citiobs.secd.eu/api/subscriptions");
        SERVER_PROPERTIES.put("plugins.websub.errorUrl", "https://github.com/securedimensions/FROST-Server-WebSub/errors");
        SERVER_PROPERTIES.put("plugins.websub.errorRel", "http://www.opengis.net/doc/is/sensorthings-websub/1.0");
    }

    private static final String[] rootTopicsOthers = {"Foo", "/", "", " ", "#"};
    private static final String[] rootTopicSingle = {"Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')"};
    private static Map<String, String[]> TEST_DATA = new HashMap<>();

    static {
        // Other test topics
        TEST_DATA.put("Foo", new String[]{"404", null, null, null});
        TEST_DATA.put("/", new String[]{"200", null, null, null});
        TEST_DATA.put("", new String[]{"200", null, null, null});
        TEST_DATA.put(" ", new String[]{"200", null, null, null});
        TEST_DATA.put("#", new String[]{"200", null, null, null});
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

            // Create a Party for testing
            createParty();
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
        for (String entity : rootTopicsSTA) {
            testDiscoveryByPath(entity, "GET");
            //testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicsSTAMultiDataStream) {
            testDiscoveryByPath(entity, "GET");
            //testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicsSTAplus) {
            testDiscoveryByPath(entity, "GET");
            //testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicSingle) {
            testDiscoveryByPath(entity, "GET");
            //testDiscoveryByPath(entity, "HEAD");
        }
        for (String entity : rootTopicsOthers) {
            testDiscoveryByPath(entity, "GET");
            //testDiscoveryByPath(entity, "HEAD");
        }

    }

    public void createParty() throws IOException {
        LOGGER.info("createParty()");
        String request = String.format("{\"description\": \"The opportunistic pirate by Robert Louis Stevenson\", \"displayName\": \"Long John Silver Citizen Scientist\", \"role\": \"individual\", \"authId\": \"%s\"}", "ff1045c2-a6de-31ad-8eb2-2be104fe27ea");
        HttpPost httpPost = new HttpPost(serverSettings.getServiceUrl(version) + "/Parties");
        HttpEntity stringEntity = new StringEntity(request, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        try (CloseableHttpResponse response = serviceSTAplus.execute(httpPost)) {

            if (response.getStatusLine().getStatusCode() == 201) {
                Assertions.assertTrue(true, "Test Party created");
            } else {
                fail(response, "Test Party creation failed");
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
            String errorLink = linkHeaders.get(SERVER_PROPERTIES.get("plugins.websub.errorRel"));
            Assertions.assertTrue(response.getStatusLine().getStatusCode() == expectedStatusCode, "response status code match");
            if (TEST_DATA.get(rootTopic)[1] == null) {
                Assertions.assertTrue(selfLink == null, "requested entityset is not in rootTopic => there is no Link rel=self");
            } else {
                String expectedSelfLink = serverSettings.getServiceUrl(version) + "/" + TEST_DATA.get(rootTopic)[1];
                Assertions.assertTrue(selfLink.equalsIgnoreCase(expectedSelfLink), "self-link match");
            }
            if (TEST_DATA.get(rootTopic)[2] != null) {
                // Testing error-Link
                String expectedErrorLink = SERVER_PROPERTIES.get("plugins.websub.errorUrl") + TEST_DATA.get(rootTopic)[3];
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

    public static class DiscoveryPathTestsAll extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams,Sensors,Things,Locations,HistoricalLocations,Observations,FeaturesOfInterest,MultiDatastreams,Parties,Licenses,Campaigns,ObservationGroups,Relations");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null, null});
            TEST_DATA.put("Sensors", new String[]{"200", "Sensors", null, null});
            TEST_DATA.put("Things", new String[]{"200", "Things", null, null});
            TEST_DATA.put("Locations", new String[]{"200", "Locations", null, null});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", "HistoricalLocations", null, null});
            TEST_DATA.put("Observations", new String[]{"200", "Observations", null, null});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", "FeaturesOfInterest", null, null});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", "MultiDatastreams", null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", "Parties", null, null});
            TEST_DATA.put("Licenses", new String[]{"200", "Licenses", null, null});
            TEST_DATA.put("Campaigns", new String[]{"200", "Campaigns", null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"200", "ObservationGroups", null, null});
            TEST_DATA.put("Relations", new String[]{"200", "Relations", null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestsAll() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTA00 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestSTA00() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTA01 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestSTA01() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTA10 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null, null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestSTA10() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTA11 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Datastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", "Datastreams", null, null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestSTA11() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD00 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestMD00() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD01 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestMD01() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD10 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", "MultiDatastreams", null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestMD10() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestMD11 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "MultiDatastreams");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", "MultiDatastreams", null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestMD11() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTAplus00 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Parties");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestSTAplus00() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTAplus01 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Parties");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"404", null, null, null});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", "Parties", null, null});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestSTAplus01() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTAplus10 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Parties");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "false");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"404", null, null, null});
            TEST_DATA.put("Licenses", new String[]{"404", null, null, null});
            TEST_DATA.put("Campaigns", new String[]{"404", null, null, null});
            TEST_DATA.put("ObservationGroups", new String[]{"404", null, null, null});
            TEST_DATA.put("Relations", new String[]{"404", null, null, null});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"404", null, null, null});
        }

        public DiscoveryPathTestSTAplus10() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class DiscoveryPathTestSTAplus11 extends DiscoveryPathTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Parties");
            SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "true");
            SERVER_PROPERTIES.put("plugins.staplus.enable", "true");

            // Core Data Model entities
            TEST_DATA.put("Datastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Sensors", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Things", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Locations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("HistoricalLocations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Observations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("FeaturesOfInterest", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // MultiDatastream
            TEST_DATA.put("MultiDatastreams", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // STAplus Data Model entities
            TEST_DATA.put("Parties", new String[]{"200", "Parties", null, null});
            TEST_DATA.put("Licenses", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Campaigns", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("ObservationGroups", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            TEST_DATA.put("Relations", new String[]{"200", null, "http://www.opengis.net/doc/is/sensorthings-websub/1.0", TAG_ERROR_ENTITY_NOT_ALLOWED});
            // Single entity test
            TEST_DATA.put("Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", new String[]{"200", "Parties('ff1045c2-a6de-31ad-8eb2-2be104fe27ea')", null, null});
        }

        public DiscoveryPathTestSTAplus11() {
            super(ServerVersion.v_1_1);
        }
    }
}
