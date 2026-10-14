package hu.rapids.common;

import java.util.List;
import hu.rapids.common.Domain.*;
public interface CommandHandler {
    String topic();
    List<Event> execute(String id,Command command,User user);
}
