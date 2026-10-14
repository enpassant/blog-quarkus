package hu.rapids.common;
import java.util.Map;
public final class Notifications {
    private Notifications() {}
    public static Map<String,String> event(Domain.Event event) {
        return Map.of("value",event.type(),"requestId",event.requestId()==null?"":event.requestId(),"id",event.id());
    }
}
