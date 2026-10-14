package hu.rapids.monitor;
import hu.rapids.common.Statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

@ApplicationScoped
@ServerEndpoint("/monitor/ws")
public class MonitorSocket {
    @Inject Statistics service;
    @Inject ObjectMapper mapper;
    @OnOpen public void open(Session session) throws Exception { send(session); }
    @OnMessage public void message(String ignored,Session session) throws Exception { send(session); }
    private void send(Session session) throws Exception { session.getAsyncRemote().sendText(mapper.writeValueAsString(service.stats())); }
}
