# SensorThings API WebSub Plugin Help Page

The SensorThings WebSub plugin implementation supports the W3C WebSub discovery. In certain cases, the discovery does not return the `Link rel="self"` header. To inform about the reason,
the implementation returns a `Link rel="help"` header where the actual link points to this HTML page, including the hashtag anchor.

This solution is required because the WebSub discovery may only add `Link` header(s) to the service response. The typical error messaging in the response body is not possible.

## Help Definitions
The following tags are defined for the `Link rel="help"` header:

<a name="entityInvalid"></a>
### #entityInvalid
The MQTT topic, resulting from the request URL is invalid.

<a name="entityNotAllowed"></a>
### #entityNotAllowed

The MQTT topic, resulting from the request URL is on a disallowed entity.

<a name="odataQueryDisabled"></a>
### #odataQueryDisabled

The MQTT topic, resulting from the request URL would include an ODATA query. But, this option is disabled.

<a name="odataQueryFilterDisabled"></a>
### #odataQueryFilterDisabled

The MQTT topic, resulting from the request URL would include an ODATA query with the command `$filter`. But, this option is disabled.

<a name="odataQueryFilterDisabled"></a>
### #odataQueryFilterDisabled

The MQTT topic, resulting from the request URL would include an ODATA query with the command `$expand`. But, this option is disabled.