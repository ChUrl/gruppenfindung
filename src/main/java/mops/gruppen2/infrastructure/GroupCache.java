package mops.gruppen2.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.exception.GroupNotFoundException;
import mops.gruppen2.domain.exception.IdMismatchException;
import mops.gruppen2.domain.exception.UserNotFoundException;
import mops.gruppen2.domain.model.group.Group;
import mops.gruppen2.domain.model.group.Type;
import mops.gruppen2.domain.service.EventStoreService;
import mops.gruppen2.domain.service.helper.ProjectionHelper;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cached alle existierenden Gruppen und einige Beziehungen.
 * Gruppen können nach Typ angefragt werden, nach ID, nach Link oder nach User.
 * Der Cache wird von den Events aktualisiert.
 * Beim Aufruf der init() Methode werden alle bisherigen Events projiziert und die Gruppen gespeichert.
 * Die Komplette Anwendung verwendet eine Instanz des Caches.
 */
@Log4j2
@RequiredArgsConstructor
@Component
@Scope("singleton")
public class GroupCache {

    private final EventStoreService eventStoreService;

    private final Map<UUID, Group> groups = new HashMap<>();
    private final Map<String, Group> links = new HashMap<>();
    private final Map<String, List<Group>> users = new HashMap<>(); // Wird vielleicht zu groß?
    private final Map<Type, List<Group>> types = new EnumMap<>(Type.class);


    // ######################################## CACHE ###########################################


    void init() {
        ProjectionHelper.project(groups, eventStoreService.findAllEvents(), this);
    }


    // ########################################### GETTERS #######################################


    public Group group(UUID groupid) {
        if (!groups.containsKey(groupid)) {
            throw new GroupNotFoundException("Gruppe ist nicht im Cache.");
        }

        return groups.get(groupid);
    }

    public Group group(String link) {
        if (!links.containsKey(link)) {
            throw new GroupNotFoundException("Link ist nicht im Cache.");
        }

        return links.get(link);
    }

    public List<Group> groups() {
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }

        return List.copyOf(groups.values());
    }

    public List<Group> userGroups(String userid) {
        if (!users.containsKey(userid)) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(users.get(userid));
    }

    public List<Group> userLectures(String userid) {
        return userGroups(userid).stream()
                                 .filter(Group::isLecture)
                                 .collect(Collectors.toUnmodifiableList());
    }

    public List<Group> userPublics(String userid) {
        return userGroups(userid).stream()
                                 .filter(Group::isPublic)
                                 .collect(Collectors.toUnmodifiableList());
    }

    public List<Group> userPrivates(String userid) {
        return userGroups(userid).stream()
                                 .filter(Group::isPrivate)
                                 .collect(Collectors.toUnmodifiableList());
    }

    public List<Group> publics() {
        if (!types.containsKey(Type.PUBLIC)) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(types.get(Type.PUBLIC));
    }

    public List<Group> privates() {
        if (!types.containsKey(Type.PRIVATE)) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(types.get(Type.PRIVATE));
    }

    public List<Group> lectures() {
        if (!types.containsKey(Type.LECTURE)) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(types.get(Type.LECTURE));
    }


    // ######################################## SETTERS ##########################################


    public void usersPut(String userid, Group group) {
        if (!group.isMember(userid)) {
            throw new UserNotFoundException("User ist kein Mitglied, Gruppe nicht gecached.");
        }
        if (!users.containsKey(userid)) {
            users.put(userid, new ArrayList<>());
            log.debug("Ein User wurde dem Cache hinzugefügt.");
        }

        users.get(userid).add(group);
    }

    public void usersRemove(String target, Group group) {
        if (!users.containsKey(target)) {
            return;
        }

        users.get(target).remove(group);
    }

    public void groupsPut(UUID groupid, Group group) {
        if (group.getId() != groupid) {
            throw new IdMismatchException("ID passt nicht zu Gruppe, Gruppe nicht gecached.");
        }

        groups.put(groupid, group);
    }

    public void groupsRemove(UUID groupid, Group group) {
        if (!groups.containsKey(groupid)) {
            return;
        }

        groups.remove(groupid);
        links.remove(group.getLink());
        group.getMembers().forEach(user -> users.get(user.getId()).removeIf(usergroup -> !usergroup.exists()));
        types.get(group.getType()).removeIf(typegroup -> !typegroup.exists());
    }

    public void linksPut(String link, Group group) {
        if (!link.equals(group.getLink())) {
            throw new IdMismatchException("Link passt nicht zu Gruppe, Gruppe nicht gecached.");
        }

        links.put(link, group);
    }

    public void linksRemove(String link) {
        if (!links.containsKey(link)) {
            return;
        }

        links.remove(link);
    }

    public void typesPut(Type type, Group group) {
        if (!types.containsKey(type)) {
            types.put(type, new ArrayList<>());
            log.debug("Ein Typ wurde dem Cache hinzugefügt.");
        }
        if (group.getType() != type) {
            throw new IdMismatchException("Typ passt nicht zu Gruppe, Gruppe nicht gecached.");
        }

        types.get(type).add(group);
    }

    public void typesRemove(Type type, Group group) {
        if (!types.containsKey(type)) {
            return;
        }

        types.get(type).remove(group);
    }
}
