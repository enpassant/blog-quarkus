package hu.rapids.common.web;


import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class Errors implements ExceptionMapper<WebApplicationException> {
    @Override public Response toResponse(WebApplicationException e) {
        return Response.fromResponse(e.getResponse()).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error",e.getMessage()==null?"Request failed":e.getMessage())).build();
    }
}
