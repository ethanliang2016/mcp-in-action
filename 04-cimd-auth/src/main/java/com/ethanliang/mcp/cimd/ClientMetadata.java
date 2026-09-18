package com.ethanliang.mcp.cimd;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ClientMetadata(
        @JsonProperty("client_id") String clientId,
        @JsonProperty("client_name") String clientName,
        @JsonProperty("redirect_uris") List<String> redirectUris,
        @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
        @JsonProperty("scope") String scope) {
}
