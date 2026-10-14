package hu.rapids.common.web;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
public final class WebResponses {
    public static Response representation(HttpHeaders headers,Object json,String html) {
        for(MediaType type:headers.getAcceptableMediaTypes()) {
            if(type.isWildcardType() || type.isCompatible(MediaType.TEXT_HTML_TYPE)) return Response.ok(html,MediaType.TEXT_HTML).build();
            if(type.isCompatible(MediaType.APPLICATION_JSON_TYPE)) return Response.ok(json,MediaType.APPLICATION_JSON).build();
        }
        throw new NotAcceptableException();
    }
}
