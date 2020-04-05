package mops.gruppen2.service;

import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.GroupType;
import mops.gruppen2.domain.User;
import mops.gruppen2.domain.Visibility;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.domain.exception.EventException;
import mops.gruppen2.domain.exception.GroupNotFoundException;
import mops.gruppen2.repository.EventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Liefert verschiedene Projektionen auf Gruppen
 */
@Service
public class ProjectionService {

    private final EventRepository eventRepository;
    private final EventStoreService eventStoreService;

    public ProjectionService(EventRepository eventRepository, EventStoreService eventStoreService) {
        this.eventRepository = eventRepository;
        this.eventStoreService = eventStoreService;
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
    //TODO: ProjectionService
    private static Group getOrCreateGroup(Map<UUID, Group> groups, UUID groupId) {
        if (!groups.containsKey(groupId)) {
            groups.put(groupId, new Group());
        }

        return groups.get(groupId);
    }

    /**
     * Wird verwendet bei der Suche nach Gruppen: Titel, Beschreibung werden benötigt.
     * Außerdem wird beachtet, ob der eingeloggte User bereits in entsprechenden Gruppen mitglied ist.
     *
     * @return Liste von projizierten Gruppen
     *
     * @throws EventException Projektionsfehler
     */
    //TODO: ProjectionService
    //TODO Rename
    @Cacheable("groups")
    public List<Group> getAllGroupWithVisibilityPublic(String userId) throws EventException {
        List<Event> groupEvents = EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("CreateGroupEvent"));
        groupEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("UpdateGroupDescriptionEvent")));
        groupEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("UpdateGroupTitleEvent")));
        groupEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("DeleteGroupEvent")));
        groupEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("UpdateUserMaxEvent")));

        List<Group> visibleGroups = projectEventList(groupEvents);

        SearchService.sortByGroupType(visibleGroups);

        return visibleGroups.stream()
                            .filter(group -> group.getType() != null)
                            .filter(group -> !eventStoreService.userInGroup(group.getId(), userId))
                            .filter(group -> group.getVisibility() == Visibility.PUBLIC)
                            .collect(Collectors.toList());
    }

    /**
     * Wird verwendet beim Gruppe erstellen bei der Parent-Auswahl: nur Titel benötigt.
     *
     * @return List of groups
     */
    @Cacheable("groups")
    //TODO: ProjectionService
    public List<Group> getAllLecturesWithVisibilityPublic() {
        List<Event> createEvents = EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("CreateGroupEvent"));
        createEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("DeleteGroupEvent")));
        createEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("UpdateGroupTitleEvent")));
        createEvents.addAll(EventStoreService.getEventsFromDTOs(eventRepository.findAllEventsByType("DeleteGroupEvent")));

        List<Group> visibleGroups = projectEventList(createEvents);

        return visibleGroups.stream()
                            .filter(group -> group.getType() == GroupType.LECTURE)
                            .filter(group -> group.getVisibility() == Visibility.PUBLIC)
                            .collect(Collectors.toList());
    }

    /**
     * Erzeugt eine neue Map wo Gruppen aus den Events erzeugt und den Gruppen_ids zugeordnet werden.
     * Die Gruppen werden als Liste zurückgegeben.
     *
     * @param events Liste an Events
     *
     * @return Liste an Projizierten Gruppen
     *
     * @throws EventException Projektionsfehler
     */
    //TODO: ProjectionService
    public List<Group> projectEventList(List<Event> events) throws EventException {
        Map<UUID, Group> groupMap = new HashMap<>();

        events.parallelStream()
              .forEachOrdered(event -> event.apply(getOrCreateGroup(groupMap, event.getGroupId())));

        return new ArrayList<>(groupMap.values());
    }

    //TODO: ProjectionService
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
    //TODO: ProjectionService
    //TODO: Nur AddUserEvents + DeleteUserEvents betrachten
    @Cacheable("groups")
    public List<Group> getUserGroups(User user) {
        List<UUID> groupIds = eventStoreService.findGroupIdsByUser(user.getId());
        List<Event> events = eventStoreService.getGroupEvents(groupIds);
        List<Group> groups = projectEventList(events);
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
    //TODO: ProjectionService
    public Group getGroupById(UUID groupId) throws EventException {
        List<UUID> groupIds = new ArrayList<>();
        groupIds.add(groupId);

        try {
            List<Event> events = eventStoreService.getGroupEvents(groupIds);
            return projectEventList(events).get(0);
        } catch (IndexOutOfBoundsException e) {
            throw new GroupNotFoundException("@UserService");
        }
    }
}
