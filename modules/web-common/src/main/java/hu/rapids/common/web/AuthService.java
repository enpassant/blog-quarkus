package hu.rapids.common.web;
import hu.rapids.common.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
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
public class AuthService {
    @Inject SecurityIdentity identity;
    @Inject hu.rapids.common.TokenService tokens;
    @ConfigProperty(name="blog.auth.dev-login", defaultValue="false") boolean devLogin;
    public User authenticate(String authorization, String cookie) {
        if(!identity.isAnonymous()) return new User(identity.getPrincipal().getName(), identity.getPrincipal().getName());
        if(authorization!=null && authorization.startsWith("Bearer ")) return verify(authorization.substring(7));
        if(cookie!=null && !cookie.isBlank()) return verify(cookie);
        if(devLogin && authorization!=null && authorization.startsWith("Basic ")) {
            try {
                String[] pair=new String(Base64.getDecoder().decode(authorization.substring(6)),StandardCharsets.UTF_8).split(":",2);
                if(pair.length==2 && !pair[0].isBlank() && pair[0].equals(pair[1])) return new User(pair[0],pair[0]);
            } catch(IllegalArgumentException ignored) { }
        }
        throw unauthorized();
    }
    public String issue(User user) { return tokens.issue(user); }
    public User verify(String token) { return tokens.verify(token); }
    private static NotAuthorizedException unauthorized() { return new NotAuthorizedException("Bearer realm=\"rapids\""); }
}
