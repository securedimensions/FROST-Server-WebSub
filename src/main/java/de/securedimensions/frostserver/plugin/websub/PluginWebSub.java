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
package de.securedimensions.frostserver.plugin.websub;

import de.fraunhofer.iosb.ilt.frostserver.path.Version;
import de.fraunhofer.iosb.ilt.frostserver.service.*;
import de.fraunhofer.iosb.ilt.frostserver.settings.ConfigDefaults;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.Settings;
import de.fraunhofer.iosb.ilt.frostserver.settings.annotation.DefaultValue;
import de.fraunhofer.iosb.ilt.frostserver.settings.annotation.DefaultValueBoolean;
import de.fraunhofer.iosb.ilt.frostserver.util.HttpMethod;
import de.fraunhofer.iosb.ilt.frostserver.util.StringHelper;

import java.util.*;

import static de.fraunhofer.iosb.ilt.frostserver.service.PluginManager.PATH_WILDCARD;
import static de.fraunhofer.iosb.ilt.frostserver.service.PluginResultFormat.FORMAT_NAME_EMPTY;
import static de.fraunhofer.iosb.ilt.frostserver.service.RequestTypeUtils.*;
import static de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings.TAG_SERVICE_ROOT_URL;
import static de.fraunhofer.iosb.ilt.frostserver.util.Constants.CONTENT_TYPE_APPLICATION_JSONPATCH;
import static de.fraunhofer.iosb.ilt.frostserver.util.Constants.REQUEST_PARAM_FORMAT;

/**
 *
 * @author securedimensions
 */
public class PluginWebSub implements PluginRootDocument, ConfigDefaults, PluginService {

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_WEBSUB = "websub.enable";

    @DefaultValue("-")
    public static final String TAG_ROOT_TOPICS = "websub.rootTopics";

    @DefaultValueBoolean(false)
    public static final String TAG_ALLOW_ODATA_QUERY = "websub.enable.odataQuery";

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_MDS_MODEL = "multiDatastream.enable";

    @DefaultValueBoolean(false)
    public static final String TAG_ENABLE_STAPLUS = "staplus.enable";
    @DefaultValue("")
    public static final String TAG_HUB_URL = "websub.hubUrl";

    @DefaultValue("/error")
    public static final String TAG_ERROR_URL = "websub.errorUrl";

    @DefaultValue("http://ogc.org/websub/1.0/error")
    public static final String TAG_ERROR_REL = "websub.errorRel";

    public static final String TAG_ERROR_ODATA_QUERY_DISABLED = "#odataQueryDisabled";
    public static final String TAG_ERROR_ODATA_FILTER_DISABLED = "#odataQueryFilterDisabled";
    public static final String TAG_ERROR_ODATA_EXPAND_DISABLED = "#odataQueryExpandDisabled";
    public static final String TAG_ERROR_ENTITY_INVALID = "#entityInvalid";
    public static final String TAG_ERROR_ENTITY_NOT_ALLOWED = "#entityNotAllowed";

    public static final ArrayList<String> rootTopicsSTA = new ArrayList<>(List.of(new String[]{"Datastreams", "Sensors", "Things", "Locations", "HistoricalLocations", "Observations", "FeaturesOfInterest"}));
    public static final ArrayList<String> rootTopicsSTAMultiDataStream = new ArrayList<>(List.of(new String[]{"MultiDatastreams"}));
    public static final ArrayList<String> rootTopicsSTAplus = new ArrayList<>(List.of(new String[]{"Parties", "Licenses", "Campaigns", "ObservationGroups", "Relations"}));

    private static final String REQUIREMENT_WEBSUB = "https://github.com/securedimensions/FROST-Server-WebSub";

    private static final String WEBSUB = "WebSub";

    private CoreSettings settings;
    @DefaultValueBoolean(false)
    private boolean enabled;
    @DefaultValueBoolean(true)
    private boolean allowOdataQuery;

    // Default is set by FROST-Server
    private boolean allowFilter, allowExpand;

    @DefaultValueBoolean(false)
    private boolean enableMD;

    @DefaultValueBoolean(false)
    private boolean enableSTAplus;
    private String hubUrl;

    private ArrayList<String> rootTopics;
    private String rootUrl, errorUrl, errorRel;

    @Override
    public InitResult init(CoreSettings settings) {
        this.settings = settings;
        Settings pluginSettings = settings.getPluginSettings();
        enabled = pluginSettings.getBoolean(TAG_ENABLE_WEBSUB, getClass());
        if (!enabled) {
            return InitResult.INIT_OK;
        }
        enableMD = pluginSettings.getBoolean(TAG_ENABLE_MDS_MODEL, getClass());
        enableSTAplus = pluginSettings.getBoolean(TAG_ENABLE_STAPLUS, getClass());
        allowOdataQuery = pluginSettings.getBoolean(TAG_ALLOW_ODATA_QUERY, getClass());
        allowFilter = settings.getMqttSettings().isAllowMqttFilter();
        allowExpand = settings.getMqttSettings().isAllowMqttExpand();
        rootUrl = settings.getSettings().get(TAG_SERVICE_ROOT_URL);
        rootUrl = (rootUrl.endsWith("/")) ? rootUrl.substring(0, rootUrl.length() - 1) : rootUrl;
        errorUrl = pluginSettings.get(TAG_ERROR_URL, getClass());
        errorUrl = (errorUrl.endsWith("/")) ? errorUrl.substring(0, errorUrl.length() - 1) : errorUrl;
        errorRel = pluginSettings.get(TAG_ERROR_REL, getClass());
        hubUrl = pluginSettings.get(TAG_HUB_URL, getClass());
        rootTopics = new ArrayList<>(Arrays.asList(pluginSettings.get(TAG_ROOT_TOPICS, "-").split(",")));
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
        return Arrays.asList(Version.V_1_0, Version.V_1_1);
    }

    @Override
    public Collection<String> getVersionedUrlPaths() {
        return List.of(PATH_WILDCARD);
    }

    @Override
    public Collection<String> getRequestTypes() {
        return Arrays.asList(
                RequestTypeUtils.GET_CAPABILITIES,
                RequestTypeUtils.CREATE,
                RequestTypeUtils.DELETE,
                RequestTypeUtils.READ,
                RequestTypeUtils.UPDATE_ALL,
                RequestTypeUtils.UPDATE_CHANGES,
                RequestTypeUtils.UPDATE_CHANGESET,
                WEBSUB);
    }

    @Override
    public String getRequestTypeFor(Version version, String path, HttpMethod method, String contentType) {
        switch (method) {
            case DELETE:
                return RequestTypeUtils.DELETE;

            case GET:
                if (path.isEmpty() || "/".equals(path)) {
                    return RequestTypeUtils.GET_CAPABILITIES;
                }
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

            case HEAD:
                return WEBSUB;

            default:
                return null;
        }
    }

    @Override
    public ServiceResponse execute(Service mainService, ServiceRequest request, ServiceResponse response) {
        String urlPath = request.getUrlPath();
        String entityName = (urlPath.equalsIgnoreCase("")) ? urlPath : urlPath.substring(1);
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
        boolean topicValid = isValidEntity(rootTopicsSTA, entityName);
        if (enableMD && isValidEntity(rootTopicsSTAMultiDataStream, entityName))
            topicValid = true;
        else if (enableSTAplus && isValidEntity(rootTopicsSTAplus, entityName))
            topicValid = true;

        if (allowOdataQuery && (request.getUrlQuery() != null)) {
            // URL compliant for the odata query values:  "," -> "%2C" and " " -> "%20"
            topicUrl += '?' + request.getUrlQuery().replaceAll(",", "%2C").replaceAll(" ", "%20");
        }
        ArrayList linkHeaders = new ArrayList<String>();
        linkHeaders.add("<%s>; rel=\"hub\"".formatted(hubUrl));
        switch (request.getRequestType()) {
            case CREATE:
            case UPDATE_ALL:
            case UPDATE_CHANGES:
            case UPDATE_CHANGESET:
                request.addParameterIfAbsent(REQUEST_PARAM_FORMAT, FORMAT_NAME_EMPTY);
                return mainService.execute(request, response);
            case WEBSUB:
                if (topicValid && isValidEntity(rootTopics, entityName)) {
                    if (!allowOdataQuery && queryPresent) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + TAG_ERROR_ODATA_QUERY_DISABLED, errorRel));
                    } else if (allowOdataQuery && (!allowFilter && filterPresent)) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + TAG_ERROR_ODATA_FILTER_DISABLED, errorRel));
                    } else if (allowOdataQuery && (!allowExpand && expandPresent)) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + TAG_ERROR_ODATA_EXPAND_DISABLED, errorRel));
                    } else {
                        linkHeaders.add("<%s>; rel=\"self\"".formatted(topicUrl));
                    }
                } else if (!topicValid) {
                    linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + TAG_ERROR_ENTITY_INVALID, errorRel));
                } else if (!isValidEntity(rootTopics, entityName)) {
                    linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + TAG_ERROR_ENTITY_NOT_ALLOWED, errorRel));
                }
                return response.addHeaders("Link", linkHeaders);
            case READ:
                if (topicValid && isValidEntity(rootTopics, entityName)) {
                    if (!allowOdataQuery && queryPresent) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + "#odataQueryDisabled", errorRel));
                    } else if (allowOdataQuery && (!allowFilter && filterPresent)) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + "#odataQueryFilterDisabled", errorRel));
                    } else if (allowOdataQuery && (!allowExpand && expandPresent)) {
                        linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + "#odataQueryExpandDisabled", errorRel));
                    } else {
                        linkHeaders.add("<%s>; rel=\"self\"".formatted(topicUrl));
                    }
                } else if (!isValidEntity(rootTopics, entityName)) {
                    linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + "#entityNotAllowed", errorRel));
                }
            default:
                if (!topicValid) {
                    linkHeaders.add("<%s>; rel=\"%s\"".formatted(errorUrl + "#entityInvalid", errorRel));
                }
                return mainService.execute(request, response.addHeaders("Link", linkHeaders));
        }
    }

    @Override
    public void modifyServiceDocument(ServiceRequest request, Map<String, Object> result) {
        Map<String, Object> serverSettings = (Map<String, Object>) result.get(Service.KEY_SERVER_SETTINGS);
        if (serverSettings == null) {
            // Nothing to add to.
            return;
        }
        Set<String> extensionList = (Set<String>) serverSettings.get(Service.KEY_CONFORMANCE_LIST);
        extensionList.add(REQUIREMENT_WEBSUB);
    }

    private boolean isValidEntity(ArrayList<String> validEntities, String entity) {

        if ((validEntities == null) || (entity == null))
            return false;

        for (String e : validEntities) {
            if (entity.startsWith(e))
                return true;
        }
        return false;
    }

}