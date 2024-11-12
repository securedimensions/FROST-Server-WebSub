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

        SERVER_PROPERTIES.put("plugins.plugins", "de.securedimensions.frostserver.plugin.websub.PluginWebSub");

        SERVER_PROPERTIES.put("plugins.websub.hubUrl", "https://websub-hub.citiobs.secd.eu/api/subscriptions");
        SERVER_PROPERTIES.put("plugins.websub.rootTopics", "Observations");
        SERVER_PROPERTIES.put("plugins.multiDatastream.enable", "false");
        SERVER_PROPERTIES.put("plugins.staplus.enable", "false");
    }

    static final Map<String, String> discoResult = new HashMap<>();

    protected static SensorThingsPlus pMdl;
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

    @Test
    public void testActivation() throws IOException {
        //testActivation("GET");
        //testActivation("HEAD");

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
            String enabled = SERVER_PROPERTIES.get("plugins.websub.enable");
            if (enabled.equalsIgnoreCase("false")) {
                Assertions.assertTrue(hubLink == null, "WebSub plugin disabled - no rel=hub link");
                Assertions.assertTrue(selfLink == null, "WebSub plugin disabled - no rel=self link");
            } else {
                Assertions.assertTrue(hubLink.equalsIgnoreCase(SERVER_PROPERTIES.get("plugins.websub.hubUrl")), "Link rel=hub equals configured hub");
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
            SERVER_PROPERTIES.put("plugins.websub.enable", "false");
        }

        public DisabledTest() {
            super(ServerVersion.v_1_1);
        }
    }

    public static class EnabledTest extends ActivationTests {

        static {
            // Test configuration
            SERVER_PROPERTIES.put("plugins.websub.enable", "true");
        }

        public EnabledTest() {
            super(ServerVersion.v_1_1);
        }
    }

}
