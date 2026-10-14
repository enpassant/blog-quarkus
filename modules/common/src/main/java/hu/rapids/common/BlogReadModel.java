package hu.rapids.common;

import java.util.List;
import hu.rapids.common.Domain.Blog;
public interface BlogReadModel {
    Blog get(String id);
    List<Blog> all();
    long count();
    void save(Blog blog);
}
