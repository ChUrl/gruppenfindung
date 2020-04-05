package mops.gruppen2.service;

import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.User;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.domain.exception.EventException;
import mops.gruppen2.domain.exception.GroupNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final GroupService groupService;
    private final EventStoreService eventStoreService;
    private final ProjectionService projectionService;

    public UserService(GroupService groupService, EventStoreService eventStoreService, ProjectionService projectionService) {
        this.groupService = groupService;
        this.eventStoreService = eventStoreService;
        this.projectionService = projectionService;
    }

    @Cacheable("groups")
    public List<Group> getUserGroups(String userId) throws EventException {
        return getUserGroups(new User(userId, "", "", ""));
    }

    /**
     * Gibt eine Liste aus Gruppen zurück, in denen sich der übergebene User befindet.
     *
     * @param user Der User
     *
     * @return Liste aus Gruppen
     */
    //TODO: Nur AddUserEvents + DeleteUserEvents betrachten
    @Cacheable("groups")
    public List<Group> getUserGroups(User user) {
        List<UUID> groupIds = eventStoreService.findGroupIdsByUser(user.getId());
        List<Event> events = groupService.getGroupEvents(groupIds);
        List<Group> groups = projectionService.projectEventList(events);
        List<Group> newGroups = new ArrayList<>();

        for (Group group : groups) {
            if (group.getMembers().contains(user)) {
                newGroups.add(group);
            }
        }
        SearchService.sortByGroupType(newGroups);

        return newGroups;
    }

    /**
     * Gibt die Gruppe zurück, die zu der übergebenen Id passt.
     *
     * @param groupId Die Id der gesuchten Gruppe
     *
     * @return Die gesuchte Gruppe
     *
     * @throws EventException Wenn die Gruppe nicht gefunden wird
     */
    public Group getGroupById(UUID groupId) throws EventException {
        List<UUID> groupIds = new ArrayList<>();
        groupIds.add(groupId);

        try {
            List<Event> events = groupService.getGroupEvents(groupIds);
            return projectionService.projectEventList(events).get(0);
        } catch (IndexOutOfBoundsException e) {
            throw new GroupNotFoundException("@UserService");
        }
    }
}
