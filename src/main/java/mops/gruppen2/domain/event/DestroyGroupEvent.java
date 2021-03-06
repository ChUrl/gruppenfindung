package mops.gruppen2.domain.event;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.exception.NoAccessException;
import mops.gruppen2.domain.model.group.Group;
import mops.gruppen2.infrastructure.GroupCache;

import java.util.UUID;

@Log4j2
@Value
@AllArgsConstructor
public class DestroyGroupEvent extends Event {

    public DestroyGroupEvent(UUID groupId, String exec) {
        super(groupId, exec, null);
    }

    @Override
    protected void updateCache(GroupCache cache, Group group) {
        cache.groupsRemove(groupid, group);
    }

    @Override
    protected void applyEvent(Group group) throws NoAccessException {
        group.destroy(exec);

        log.trace("\t\t\t\t\tGelöschte Gruppe: {}", group.toString());
    }

    @Override
    public String format() {
        return "Gruppe gelöscht.";
    }

    @Override
    public String type() {
        return EventType.DESTROYGROUP.toString();
    }
}
