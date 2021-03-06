package mops.gruppen2.domain.service.helper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.domain.model.group.Group;
import mops.gruppen2.infrastructure.GroupCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liefert verschiedene Projektionen auf Gruppen.
 * Benötigt ausschließlich den EventStoreService.
 */
@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProjectionHelper {

    public static List<Group> project(List<Event> events) {
        Map<UUID, Group> groups = new HashMap<>();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        log.trace(groups);
        log.trace(events);

        events.forEach(event -> event.apply(getOrCreateGroup(groups, event.getGroupid())));

        return new ArrayList<>(groups.values());
    }

    public static void project(Map<UUID, Group> groups, List<Event> events, GroupCache cache) {
        if (events.isEmpty()) {
            return;
        }

        log.trace(groups);
        log.trace(events);

        events.forEach(event -> event.apply(getOrCreateGroup(groups, event.getGroupid()), cache));
    }

    /**
     * Gibt die Gruppe mit der richtigen Id aus der übergebenen Map wieder, existiert diese nicht
     * wird die Gruppe erstellt und der Map hizugefügt.
     *
     * @param groups  Map aus GruppenIds und Gruppen
     * @param groupId Die Id der Gruppe, die zurückgegeben werden soll
     *
     * @return Die gesuchte Gruppe
     */
    private static Group getOrCreateGroup(Map<UUID, Group> groups, UUID groupId) {
        if (!groups.containsKey(groupId)) {
            groups.put(groupId, Group.EMPTY());
        }

        return groups.get(groupId);
    }
}
