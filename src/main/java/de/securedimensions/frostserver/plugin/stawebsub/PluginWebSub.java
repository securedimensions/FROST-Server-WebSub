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

import static de.fraunhofer.iosb.ilt.frostserver.service.PluginManager.PATH_WILDCARD;
import static de.fraunhofer.iosb.ilt.frostserver.service.PluginResultFormat.FORMAT_NAME_EMPTY;
import static de.fraunhofer.iosb.ilt.frostserver.service.RequestTypeUtils.*;
import static de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings.TAG_SERVICE_ROOT_URL;
import static de.fraunhofer.iosb.ilt.frostserver.util.Constants.CONTENT_TYPE_APPLICATION_JSONPATCH;
import static de.fraunhofer.iosb.ilt.frostserver.util.Constants.REQUEST_PARAM_FORMAT;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.fraunhofer.iosb.ilt.frostserver.json.deserialize.JsonReaderDefault;
import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.request.ServiceRequest;
import de.fraunhofer.iosb.ilt.frostserver.request.Version;
import de.fraunhofer.iosb.ilt.frostserver.service.*;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.util.HttpMethod;
import de.fraunhofer.iosb.ilt.frostserver.util.StringHelper;
import de.fraunhofer.iosb.ilt.settings.ConfigDefaults;
import de.fraunhofer.iosb.ilt.settings.Settings;
import de.fraunhofer.iosb.ilt.settings.annotation.DefaultValue;
import de.fraunhofer.iosb.ilt.settings.annotation.DefaultValueBoolean;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author securedimensions
 */
public class PluginWebSub implements PluginRootDocument, ConfigDefaults, PluginService {

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_WEBSUB = "stawebsub.enable";

    @DefaultValue("")
    public static final String TAG_TOPICS_DENIED = "stawebsub.topicsDenied";

    @DefaultValueBoolean(false)
    public static final String TAG_ALLOW_ODATA_QUERY = "stawebsub.enable.odataQuery";

    @DefaultValue("")
    public static final String TAG_HUB_URL = "stawebsub.hubUrl";

    @DefaultValue("/error")
    public static final String TAG_HELP_URL = "stawebsub.helpUrl";

    public static final String TAG_ERROR_ODATA_QUERY_DISABLED = "odataQueryDisabled";
    public static final String TAG_ERROR_ODATA_FILTER_DISABLED = "odataQueryFilterDisabled";
    public static final String TAG_ERROR_ODATA_EXPAND_DISABLED = "odataQueryExpandDisabled";
    public static final String TAG_ERROR_ENTITY_INVALID = "entityInvalid";
    public static final String TAG_ERROR_TOPIC_NOT_ALLOWED = "topicNotAllowed";

    public static final String REQUIREMENT_WEBSUB = "https://www.opengis.net/spec/sensorthings-websub/1.0/conf/discovery";

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginWebSub.class.getName());

    private CoreSettings settings;
    @DefaultValueBoolean(false)
    private boolean enabled;
    @DefaultValueBoolean(true)
    private boolean allowOdataQuery;

    // Default is set by FROST-Server
    private boolean allowFilter, allowExpand;

    private String hubUrl;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ArrayList<String> deniedTopics;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private ArrayList<String> deniedOdata;

    private String rootUrl, helpUrl;

    @Override
    public InitResult init(CoreSettings settings) {
        this.settings = settings;
        Settings pluginSettings = settings.getPluginSettings();
        enabled = pluginSettings.getBoolean(TAG_ENABLE_WEBSUB, getClass());
        if (!enabled) {
            return InitResult.INIT_OK;
        }

        allowOdataQuery = pluginSettings.getBoolean(TAG_ALLOW_ODATA_QUERY, getClass());
        allowFilter = settings.getMqttSettings().isAllowMqttFilter();
        allowExpand = settings.getMqttSettings().isAllowMqttExpand();
        rootUrl = settings.getSettings().get(TAG_SERVICE_ROOT_URL);
        rootUrl = (rootUrl.endsWith("/")) ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
        helpUrl = pluginSettings.get(TAG_HELP_URL, getClass());
        helpUrl = (helpUrl.endsWith("/")) ? helpUrl.substring(0, helpUrl.length() - 1) : helpUrl;
        helpUrl = helpUrl + "#";
        hubUrl = pluginSettings.get(TAG_HUB_URL, getClass());
        deniedOdata = new ArrayList<>();
        String dt = pluginSettings.get(TAG_TOPICS_DENIED, getClass());
        if (dt.equalsIgnoreCase(""))
            deniedTopics = new ArrayList<>();
        else
            deniedTopics = new ArrayList<>(Arrays.asList(dt.split(",")));

        if (enabled) {
            settings.getPluginManager().registerPlugin(this);
        }

        return InitResult.INIT_OK;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<Version> getVersions() {
        return this.settings.getPluginManager().getVersions().values();
    }

    @Override
    public Collection<String> getVersionedUrlPaths() {
        return List.of(PATH_WILDCARD);
    }

    @Override
    public Collection<String> getRequestTypes() {
        return Arrays.asList(
                RequestTypeUtils.CREATE,
                RequestTypeUtils.DELETE,
                RequestTypeUtils.READ,
                RequestTypeUtils.UPDATE_ALL,
                RequestTypeUtils.UPDATE_CHANGES,
                RequestTypeUtils.UPDATE_CHANGESET);
    }

    @Override
    public String getRequestTypeFor(Version version, String path, HttpMethod method, String contentType) {
        switch (method) {
            case DELETE:
                return RequestTypeUtils.DELETE;

            case HEAD:
            case GET:
                // FROST 2.8+: root document is handled as READ (Service.handleGet →
                // handleGetCapabilities). Do not return a custom type here or the
                // landing page never gets serverSettings / modifyServiceDocument.
                return RequestTypeUtils.READ;

            case PATCH:
                if (!StringHelper.isNullOrEmpty(contentType) && contentType.startsWith(CONTENT_TYPE_APPLICATION_JSONPATCH)) {
                    return RequestTypeUtils.UPDATE_CHANGESET;
                }
                return RequestTypeUtils.UPDATE_CHANGES;

            case POST:
                return RequestTypeUtils.CREATE;

            case PUT:
                return RequestTypeUtils.UPDATE_ALL;

            default:
                return null;
        }
    }

    @Override
    public ServiceResponse execute(Service mainService, ServiceRequest request, ServiceResponse response) {
        // Required when this plugin is the request-type handler (replaces PluginCoreService).
        request.getContext().setJsonReader(new JsonReaderDefault(
                request.getModelRegistry(), request.getVersion(), request.getUserPrincipal()));

        String urlPath = request.getUrlPath();
        String entityName = (urlPath.isEmpty()) ? urlPath : urlPath.substring(1);
        String topic = request.getVersion() + "/" + entityName;
        String topicUrl = rootUrl + "/" + request.getVersion() + request.getUrlPath();
        String odataQuery = request.getUrlQuery();
        boolean queryPresent = odataQuery != null;
        boolean filterPresent = false;
        if (odataQuery != null) {
            filterPresent = odataQuery.contains("filter=");
        }
        boolean expandPresent = false;
        if (odataQuery != null) {
            expandPresent = odataQuery.contains("expand=");
        }

        Iterator<EntityType> eti = settings.getModelRegistry().getEntityTypes().iterator();
        // True if the entityName is from the data model entities that are activated
        boolean validEntity = false;
        while (eti.hasNext()) {
            EntityType et = eti.next();
            if (entityName.startsWith(et.plural)) {
                validEntity = true;
                break;
            }
        }

        if (allowOdataQuery && (request.getUrlQuery() != null)) {
            // URL compliant for the odata query values:  "," -> "%2C" and " " -> "%20"
            topicUrl += '?' + request.getUrlQuery().replaceAll(",", "%2C").replaceAll(" ", "%20");
        }
        ArrayList linkHeaders = new ArrayList<String>();
        linkHeaders.add("<%s>; rel=\"hub\"".formatted(hubUrl));
        switch (request.getRequestType()) {
            case CREATE, UPDATE_ALL, UPDATE_CHANGES, UPDATE_CHANGESET -> {
                request.addParameter(REQUEST_PARAM_FORMAT, FORMAT_NAME_EMPTY);
                return mainService.execute(request, response);
            }
            case READ -> {
                // Landing page: advertise hub only; Service builds the root document.
                if (urlPath.isEmpty() || "/".equals(urlPath)) {
                    return mainService.execute(request, response.addHeaders("Link", linkHeaders));
                }
                if (validEntity) {
                    if (isTopicDenied(deniedTopics, topic) == false) {
                        if (!allowOdataQuery && queryPresent) {
                            linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ODATA_QUERY_DISABLED));
                        } else if (allowOdataQuery && (!allowFilter && filterPresent) && (!allowExpand && expandPresent)) {
                            linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ODATA_FILTER_DISABLED));
                            linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ODATA_EXPAND_DISABLED));
                        } else if (allowOdataQuery && (!allowFilter && filterPresent)) {
                            linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ODATA_FILTER_DISABLED));
                        } else if (allowOdataQuery && (!allowExpand && expandPresent)) {
                            linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ODATA_EXPAND_DISABLED));
                        } else {
                            linkHeaders.add("<%s>; rel=\"self\"".formatted(topicUrl));
                        }
                    } else {
                        linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_TOPIC_NOT_ALLOWED));
                    }
                } else {
                    linkHeaders.add("<%s>; rel=\"help\"".formatted(helpUrl + TAG_ERROR_ENTITY_INVALID));
                }

            }
        }
        return mainService.execute(request, response.addHeaders("Link", linkHeaders));
    }

    @Override
    public void modifyServiceDocument(ServiceRequest request, Map<String, Object> result) {
        Map<String, Object> serverSettings = (Map<String, Object>) result.get(Service.KEY_SERVER_SETTINGS);
        if (serverSettings == null) {
            // Nothing to add to.
            return;
        }
        Set<String> conformanceList = (Set<String>) serverSettings.get(Service.KEY_CONFORMANCE_LIST);
        conformanceList.add(REQUIREMENT_WEBSUB);

        Map<String, Object> webSub = new HashMap();
        webSub.put("topics_denied", deniedTopics);

        if (allowOdataQuery == false) {
            // all ODATA options
            // https://docs.ogc.org/is/15-078r6/15-078r6.html §9.3.1
            webSub.put("odata_denied", new String[]{"$filter", "$count", "$orderby", "$skip", "$top", "$expand", "$select"});
        } else {
            if ((!allowExpand) && (!allowFilter)) {
                webSub.put("odata_denied", new String[]{"$expand", "$filter"});
            } else if (!allowExpand) {
                webSub.put("odata_denied", new String[]{"$expand"});
            } else if (!allowFilter) {
                webSub.put("odata_denied", new String[]{"$filter"});
            } else {
                webSub.put("odata_denied", deniedOdata);
            }

        }
        serverSettings.put(REQUIREMENT_WEBSUB, webSub);
        LOGGER.debug("serverSettings: {}", serverSettings);
    }

    private boolean isTopicDenied(ArrayList<String> deniedEntities, String entity) {

        if (deniedEntities == null)
            return false;

        if (entity == null)
            return false;

        for (String e : deniedEntities) {
            if (entity.equalsIgnoreCase(e))
                return true;
        }
        return false;
    }

}
