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

import de.fraunhofer.iosb.ilt.frostclient.SensorThingsService;
import de.fraunhofer.iosb.ilt.frostclient.models.*;
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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
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
public abstract class ActivationTests extends AbstractTestClass {

    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivationTests.class);
    private static final long serialVersionUID = 1639739965;
    private static final Map<String, String> SERVER_PROPERTIES = new LinkedHashMap<>();

    static {
        SERVER_PROPERTIES.put("mqtt.Enabled", "false");
        SERVER_PROPERTIES.put("mqtt.enabled", "false");

        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.stawebsub.PluginWebSub");

        SERVER_PROPERTIES.put("plugins.stawebsub.hubUrl", "http://hub.local");
        SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
        SERVER_PROPERTIES.put("plugins.staplus.enable", "false");
    }

    protected static SensorThingsService serviceSTAplus;

    public ActivationTests(ServerVersion version) {
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

    protected boolean contains(JSONArray a, String s) {
        for (int i = 0; i < a.length(); i++) {
            if (a.getString(i).equalsIgnoreCase(s))
                return true;
        }
        return false;
    }

    @Test
    public void testActivation() throws IOException {
        testActivation("GET");
        testActivation("HEAD");

    }

    private void testActivation(String method) throws IOException {
        LOGGER.info("  testActivation " + method);
        String url = serverSettings.getServiceUrl(version) + "/Observations";
        HttpRequestBase http = null;
        if (method.equalsIgnoreCase("GET"))
            http = new HttpGet(url.trim());
        else
            http = new HttpHead(url.trim());

        try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
            Map<String, String> linkHeaders = getLinkHeaders(response.getHeaders("Link"));
            String hubLink = linkHeaders.get("hub");
            String selfLink = linkHeaders.get("self");
            String enabled = SERVER_PROPERTIES.get("plugins.stawebsub.enable");
            if (enabled.equalsIgnoreCase("false")) {
                Assertions.assertTrue(hubLink == null, "WebSub plugin disabled - no rel=hub link");
                Assertions.assertTrue(selfLink == null, "WebSub plugin disabled - no rel=self link");
            } else {
                Assertions.assertTrue(hubLink.equalsIgnoreCase(SERVER_PROPERTIES.get("plugins.stawebsub.hubUrl")), "Link rel=hub equals configured hub");
                Assertions.assertTrue(selfLink.equalsIgnoreCase(url));
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

    public static class DisabledTest extends ActivationTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "false");
        }

        public DisabledTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            String url = serverSettings.getServiceUrl(version);
            HttpRequestBase http = new HttpGet(url.trim());

            try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
                JSONObject landingPage = new JSONObject(new String(response.getEntity().getContent().readAllBytes()));
                LOGGER.debug("landingPage: ", landingPage);
                JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
                JSONArray conformance = (JSONArray) serverSettings.get("conformance");
                Assertions.assertTrue(this.contains(conformance, REQUIREMENT_WEBSUB) == false, "WebSub plugin disabled");
            }
        }
    }

    public static class EnabledTest extends ActivationTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.stawebsub.enable", "true");
        }

        public EnabledTest() {
            super(ServerVersion.V_1_1);
        }

        @Test
        public void testLandingPage() throws IOException {
            String url = serverSettings.getServiceUrl(version);
            HttpRequestBase http = new HttpGet(url.trim());

            try (CloseableHttpResponse response = serviceSTAplus.execute(http)) {
                JSONObject landingPage = new JSONObject(new String(response.getEntity().getContent().readAllBytes()));
                LOGGER.debug("landingPage: ", landingPage);
                JSONObject serverSettings = (JSONObject) landingPage.get("serverSettings");
                JSONArray conformance = (JSONArray) serverSettings.get("conformance");
                Assertions.assertTrue(this.contains(conformance, REQUIREMENT_WEBSUB) == true, "WebSub plugin enabled");
            }
        }
    }

}
