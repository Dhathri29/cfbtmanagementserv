
package com.paypal.sre.cfbt.data.execapi;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;


/**
 * Holds UserInfo from the request header.
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "userid",
    "scopes"
})
public class UserInfo {

    /**
     * Identifier of the user performing the action.
     * (Required)
     * 
     */
    @JsonProperty("userid")
    @NotNull
    private String userid;
    /**
     * Limits what the user can do on the platform.
     * (Required)
     * 
     */
    @JsonProperty("scopes")
    @NotNull
    private List<String> scopes = new ArrayList<String>();

    /**
     * Identifier of the user performing the action.
     * (Required)
     * 
     */
    @JsonProperty("userid")
    public String getUserid() {
        return userid;
    }

    /**
     * Identifier of the user performing the action.
     * (Required)
     * 
     */
    @JsonProperty("userid")
    public void setUserid(String userid) {
        this.userid = userid;
    }

    /**
     * Limits what the user can do on the platform.
     * (Required)
     * 
     */
    @JsonProperty("scopes")
    public List<String> getScopes() {
        return scopes;
    }

    /**
     * Limits what the user can do on the platform.
     * (Required)
     * 
     */
    @JsonProperty("scopes")
    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
 
    public static String user(String userInfo) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        if (userInfo != null) {
            UserInfo userInfoObj = mapper.readValue(userInfo, UserInfo.class);
            return userInfoObj.getUserid();
        }

        throw new IllegalArgumentException("UserInfo is null");
    }
}
