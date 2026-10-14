package hu.rapids.common;

import java.util.List;
import hu.rapids.common.Domain.Discussion;
public interface DiscussionReadModel {
    Discussion get(String id);
    List<Discussion> all();
    long count();
    void save(Discussion discussion);
}
