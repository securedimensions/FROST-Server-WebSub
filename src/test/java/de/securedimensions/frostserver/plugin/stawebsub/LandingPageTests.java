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
package de.securedimensions.frostserver.plugin.stawebsub;

import static de.securedimensions.frostserver.plugin.stawebsub.PluginWebSub.REQUIREMENT_WEBSUB;
import static org.junit.Assert.fail;

import de.fraunhofer.iosb.ilt.frostclient.SensorThingsService;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsPlus;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsV11MultiDatastream;
import de.fraunhofer.iosb.ilt.frostclient.models.SensorThingsV11Sensing;
import de.fraunhofer.iosb.ilt.statests.AbstractTestClass;
import de.fraunhofer.iosb.ilt.statests.ServerVersion;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests plugin enabled / disabled
 * e.g. http://localhost:8080/FROST-Server/v1.1/Observations
 *
 * @author Andreas Matheus
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class LandingPageTests extends AbstractTestClass {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(LandingPageTests.class);
    private static final long serialVersionUID = 1639739965;
    private static final Map<String, String> SERVER_PROPERTIES = new LinkedHashMap<>();

    static {
        SERVER_PROPERTIES.put("mqtt.Enabled", "false");
        SERVER_PROPERTIES.put("mqtt.enabled", "false");

        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.stawebsub.PluginWebSub");

        SERVER_PROPERTIES.put("plugins.stawebsub.hubUrl", "https://websub-hub.citiobs.secd.eu/api/subscriptions");
        SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
        SERVER_PROPERTIES.put("plugins.staplus.enable", "false");
    }

    protected static SensorThingsService serviceSTAplus;

    public LandingPageTests(ServerVersion version) {
        super(version, SERVER_PROPERTIES);
    }

    @AfterAll
    public static void tearDown() {
        LOGGER.info("Tearing down.");
    }

    @Override
    protected void setUpVersion() {
        LOGGER.info("Setting up for version {}.", version.urlPart);
        serviceSTAplus = new SensorThingsService(
                new SensorThingsV11Sensing(),
                new SensorThingsV11MultiDatastream(),
                new SensorThingsPlus());
        try {
            serviceSTAplus.setBaseUrl(new URL(serverSettings.getServiceUrl(version)));
            serviceSTAplus.init();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected JSONObject getLandingPage() throws IOException {
        String url = serverSettings.getServiceUrl(version);
        HttpRequestBase http = new HttpGet(url.trim());

        try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
            JSONObject landingPage = new JSONObject(new String(response.getEntity().getContent().readAllBytes()));
            LOGGER.debug("landingPage: ", landingPage);
            return landingPage;
        }
    }

    protected boolean contains(JSONArray a, String s) {
        for (int i = 0; i < a.length(); i++) {
            if (a.getString(i).equalsIgnoreCase(s))
                return true;
        }
        return false;
    }

    protected boolean equals(JSONArray a, ArrayList b) {
        for (int i = 0; i < a.length(); i++) {
            if (!b.contains(b.get(i)))
                return false;
        }
        return true;
    }

    public static class LandingPageNoTopicsNoOdataDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "true");
        }

        public LandingPageNoTopicsNoOdataDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            // There is no entry for REQUIREMENT_WEBSUB if both topic and odata have no denied entries
            if (serverSettings.has(REQUIREMENT_WEBSUB) == false) {
                // no constraints for both topics and odata options
                Assertions.assertTrue(true, "both `odata_denied` and `topics_denied` do not exist");
            } else {
                // If there are `topics_denied` then there is REQUIREMENT_WEBSUB and we need to assert that `odata_denied` does not exist or is empty
                JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
                JSONArray odataDenied = ((JSONArray) webSub.get("odata_denied"));
                if (odataDenied.isEmpty())
                    Assertions.assertTrue(true, "`odata_denied` array exists and is empty");
                else
                    fail("`odata_denied` is not empty");
            }
        }
    }

    public static class LandingPageTopicObservationsDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.topicsDenied", "v1.1/Observations");
        }

        public LandingPageTopicObservationsDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            Assertions.assertTrue(serverSettings.has(REQUIREMENT_WEBSUB), REQUIREMENT_WEBSUB + " exists");

            JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
            JSONArray topicsDenied = (JSONArray) webSub.get("topics_denied");
            Assertions.assertTrue(topicsDenied.length() == 1, "`topics_denied` has one entry");
            Assertions.assertTrue(topicsDenied.get(0).toString().equalsIgnoreCase("v1.1/Observations"), "topics_denied=[\"v1.1/Observations\"]");
        }
    }

    public static class LandingPageNoODataDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "false");
        }

        public LandingPageNoODataDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            Assertions.assertTrue(serverSettings.has(REQUIREMENT_WEBSUB), REQUIREMENT_WEBSUB + " exists");

            JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
            JSONArray odataDenied = (JSONArray) webSub.get("odata_denied");
            Assertions.assertTrue(odataDenied.length() == 7, "`odata_denied` has 7 entries");

            ArrayList<String> expectedOdataDenied = new ArrayList(Arrays.asList("$filter", "$count", "$orderby", "$skip", "$top", "$expand", "$select"));
            Assertions.assertTrue(this.equals(odataDenied, expectedOdataDenied), "odata_denied=[\"$filter\", \"$count\", \"$orderby\", \"$skip\", \"$top\", \"$expand\", \"$select\"]");
        }
    }

    public static class LandingPageODataExpandDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "false");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "true");
        }

        public LandingPageODataExpandDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            Assertions.assertTrue(serverSettings.has(REQUIREMENT_WEBSUB), REQUIREMENT_WEBSUB + " exists");

            JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
            JSONArray odataDenied = (JSONArray) webSub.get("odata_denied");
            Assertions.assertTrue(odataDenied.length() == 1, "`odata_denied` has 1 entry");

            ArrayList<String> expectedOdataDenied = new ArrayList(Arrays.asList("$expand"));
            Assertions.assertTrue(this.equals(odataDenied, expectedOdataDenied), "odata_denied=[\"$expand\"]");
        }
    }

    public static class LandingPageODataFilterDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "true");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "false");
        }

        public LandingPageODataFilterDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            Assertions.assertTrue(serverSettings.has(REQUIREMENT_WEBSUB), REQUIREMENT_WEBSUB + " exists");

            JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
            JSONArray odataDenied = (JSONArray) webSub.get("odata_denied");
            Assertions.assertTrue(odataDenied.length() == 1, "`odata_denied` has 1 entry");

            ArrayList<String> expectedOdataDenied = new ArrayList(Arrays.asList("$filter"));
            Assertions.assertTrue(this.equals(odataDenied, expectedOdataDenied), "odata_denied=[\"$filter\"]");
        }
    }

    public static class LandingPageODataExpandFilterDeniedTest extends LandingPageTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
            SERVER_PROPERTIES.put("plugins.stawebsub.enable.odataQuery", "true");
            SERVER_PROPERTIES.put("mqtt.allowExpand", "false");
            SERVER_PROPERTIES.put("mqtt.allowFilter", "false");
        }

        public LandingPageODataExpandFilterDeniedTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            JSONObject landingPage = this.getLandingPage();
            JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
            Assertions.assertTrue(serverSettings.has(REQUIREMENT_WEBSUB), REQUIREMENT_WEBSUB + " exists");

            JSONObject webSub = (JSONObject) serverSettings.get(REQUIREMENT_WEBSUB);
            JSONArray odataDenied = (JSONArray) webSub.get("odata_denied");
            Assertions.assertTrue(odataDenied.length() == 2, "`odata_denied` has 2 entries");

            ArrayList<String> expectedOdataDenied = new ArrayList(Arrays.asList("$expand", "$filter"));
            Assertions.assertTrue(this.equals(odataDenied, expectedOdataDenied), "odata_denied=[\"$expand\",\"$filter\"]");
        }
    }
}
