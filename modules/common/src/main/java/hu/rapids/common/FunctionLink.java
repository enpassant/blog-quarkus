package hu.rapids.common;
import com.fasterxml.jackson.annotation.JsonProperty;
public record FunctionLink(@JsonProperty("_t") String type,int order,String url,String title,
                           String serviceId,String instanceId,String baseUrl,long expiresAt,boolean navigation) {}
