package hu.rapids.common;
import hu.rapids.common.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static hu.rapids.common.Domain.*;

@ApplicationScoped
public class TokenService {
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="blog.auth.secret") Optional<String> configuredSecret;
    @ConfigProperty(name="blog.kafka.enabled",defaultValue="false") boolean kafkaEnabled;
    private String secret;
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    void validate(@jakarta.enterprise.event.Observes @jakarta.annotation.Priority(1000) io.quarkus.runtime.StartupEvent startup) {
        // CDI initializes this bean before starting the Kafka consumers.
    }
    @PostConstruct void init() {
        if(kafkaEnabled && configuredSecret.isEmpty()) throw new IllegalStateException("Set the same BLOG_AUTH_SECRET for all Kafka-connected services");
        secret=configuredSecret.orElseGet(() -> UUID.randomUUID()+"-"+UUID.randomUUID());
        if(configuredSecret.isPresent() && secret.length()<32) throw new IllegalStateException("BLOG_AUTH_SECRET must contain at least 32 characters");
    }
    public String issue(User user) {
        try {
            String header=encode(mapper.writeValueAsBytes(Map.of("typ","JWT","alg","HS256")));
            String payload=encode(mapper.writeValueAsBytes(Map.of("sub",user.userId(),"name",user.userName(),"exp",System.currentTimeMillis()/1000+300,"jti",UUID.randomUUID().toString())));
            String signed=header+"."+payload;
            return signed+"."+encode(hmac(signed));
        } catch(Exception e) { throw new IllegalStateException("Cannot issue token",e); }
    }
    public User verify(String token) {
        return verify(token,System.currentTimeMillis()/1000);
    }
    /** Kafka is internal transport: check expiry at acceptance, not after queueing. */
    public User verifyQueued(String token,long acceptedAtMillis) {
        return verify(token,acceptedAtMillis>0?acceptedAtMillis/1000:System.currentTimeMillis()/1000);
    }
    /** Only persisted requests may resume delivery after their session expires. */
    public User verifyPersisted(String token) { return verify(token,Long.MIN_VALUE); }
    private User verify(String token,long validAtSeconds) {
        try {
            String[] parts=token.split("\\.");
            if(parts.length!=3) throw unauthorized();
            var header=mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if(!"HS256".equals(header.path("alg").asText())) throw unauthorized();
            if(!MessageDigest.isEqual(hmac(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2]))) throw unauthorized();
            var payload=mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String id=payload.path("sub").asText();
            if(id.isBlank() || payload.path("exp").asLong()<=validAtSeconds) throw unauthorized();
            return new User(id,payload.path("name").asText(id));
        } catch(Exception e) { throw unauthorized(); }
    }
    private byte[] hmac(String data) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
    private static String encode(byte[] bytes) { return ENCODER.encodeToString(bytes); }
    private static NotAuthorizedException unauthorized() { return new NotAuthorizedException("Bearer realm=\"rapids\""); }
}
