package hu.rapids.web;
import hu.rapids.common.web.AuthService;
import hu.rapids.common.Domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

@ApplicationScoped
@ServerEndpoint("/updates/{id}")
public class UpdatesSocket implements hu.rapids.common.MessageHandler {
    @Inject AuthService auth;
    private final Map<Session,String> sessions=new ConcurrentHashMap<>();
    private static final Logger LOG=Logger.getLogger(UpdatesSocket.class);
    @OnOpen public void open(Session session,@PathParam("id") String id) throws IOException {
        try {
            String token=session.getRequestParameterMap().getOrDefault("token",java.util.List.of("")).getFirst();
            if(!auth.verify(token).userId().equals(id)) throw new IllegalArgumentException();
            sessions.put(session,id);
        } catch(RuntimeException e) { session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,"Authentication required")); }
    }
    @OnClose public void close(Session session) { sessions.remove(session); }
    @OnError public void error(Session session,Throwable error) { sessions.remove(session); LOG.debug("Update socket closed",error); }
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final String group="webapp-updates-"+java.util.UUID.randomUUID();
    public String group() { return group; }
    public java.util.List<String> topics() { return java.util.List.of("client-commands"); }
    public void handle(hu.rapids.common.MessageBus.Message message) { publishJson(message.key(),message.value()); }
    public void publish(String userId,String type) {
        publishJson(userId,"{\"value\":\""+type+"\"}");
    }
    private void publishJson(String userId,String json) {
        sessions.forEach((session,id) -> {
            if(id.equals(userId) && session.isOpen()) session.getAsyncRemote().sendText(json, result -> {
                if(!result.isOK()) sessions.remove(session);
            });
        });
    }
}
