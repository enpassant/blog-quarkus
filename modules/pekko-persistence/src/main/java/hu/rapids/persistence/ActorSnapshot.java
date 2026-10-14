package hu.rapids.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import hu.rapids.common.Domain.Event;
import hu.rapids.common.PersistencePayload;
import java.util.List;

public record ActorSnapshot(@JsonProperty("_t") String type, JsonNode state,
                            List<Event> completedRequests) implements PersistencePayload {
    public ActorSnapshot(JsonNode state,List<Event> completedRequests) {
        this("RapidsActorSnapshot",state,completedRequests);
    }
}
