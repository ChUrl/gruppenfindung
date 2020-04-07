package mops.gruppen2.service;

import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.GroupType;
import mops.gruppen2.domain.Role;
import mops.gruppen2.domain.User;
import mops.gruppen2.domain.Visibility;
import mops.gruppen2.domain.event.AddUserEvent;
import mops.gruppen2.domain.event.CreateGroupEvent;
import mops.gruppen2.domain.event.DeleteGroupEvent;
import mops.gruppen2.domain.event.DeleteUserEvent;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.domain.event.UpdateGroupDescriptionEvent;
import mops.gruppen2.domain.event.UpdateGroupTitleEvent;
import mops.gruppen2.domain.event.UpdateRoleEvent;
import mops.gruppen2.domain.event.UpdateUserLimitEvent;
import mops.gruppen2.domain.exception.EventException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Behandelt Aufgaben, welche sich auf eine Gruppe beziehen.
 * Es werden übergebene Gruppen bearbeitet und dementsprechend Events erzeugt und gespeichert.
 */
@Service
@Log4j2
public class GroupService {

    private final EventStoreService eventStoreService;
    private final ValidationService validationService;
    private final InviteService inviteService;

    public GroupService(EventStoreService eventStoreService, ValidationService validationService, InviteService inviteService) {
        this.eventStoreService = eventStoreService;
        this.validationService = validationService;
        this.inviteService = inviteService;
    }


    // ################################# GRUPPE ERSTELLEN ########################################


    /**
     * Erzeugt eine neue Gruppe, fügt den User, der die Gruppe erstellt hat, hinzu und setzt seine Rolle als Admin fest.
     * Zudem wird der Gruppentitel und die Gruppenbeschreibung erzeugt, welche vorher der Methode übergeben wurden.
     * Aus diesen Event-Objekten wird eine Liste erzeugt, welche daraufhin mithilfe des EventServices gesichert wird.
     *
     * @param user        Keycloak-Account
     * @param title       Gruppentitel
     * @param description Gruppenbeschreibung
     */
    public Group createGroup(User user,
                             String title,
                             String description,
                             Visibility visibility,
                             GroupType groupType,
                             long userLimit,
                             UUID parent) {

        Group group = createGroup(user, parent, groupType, visibility, userLimit);

        addUser(user, group);
        updateTitle(user, group, title);
        updateDescription(user, group, description);
        updateRole(user, group, Role.ADMIN);

        inviteService.createLink(group);

        return group;
    }


    // ################################### GRUPPEN ÄNDERN ########################################


    /**
     * Fügt eine Liste von Usern zu einer Gruppe hinzu (in der Datenbank).
     * Duplikate werden übersprungen, die erzeugten Events werden gespeichert.
     * Dabei wird das Teilnehmermaximum eventuell angehoben.
     *
     * @param newUsers Userliste
     * @param group    Gruppe
     * @param user     Ausführender User
     */
    public void addUsersToGroup(List<User> newUsers, Group group, User user) {
        updateUserLimit(user, group, getAdjustedUserLimit(newUsers, group));

        newUsers.forEach(newUser -> addUserSilent(newUser, group));
    }

    /**
     * Ermittelt ein passendes Teilnehmermaximum.
     * Reicht das alte Maximum, wird dieses zurückgegeben.
     * Ansonsten wird ein erhöhtes Maximum zurückgegeben.
     *
     * @param newUsers Neue Teilnehmer
     * @param group    Bestehende Gruppe, welche verändert wird
     *
     * @return Das neue Teilnehmermaximum
     */
    private static long getAdjustedUserLimit(List<User> newUsers, Group group) {
        return Math.max(group.getMembers().size() + newUsers.size(), group.getMembers().size());
    }

    /**
     * Wechselt die Rolle eines Teilnehmers von Admin zu Member oder andersherum.
     *
     * @param user  Teilnehmer, welcher geändert wird
     * @param group Gruppe, in welcher sih der Teilnehmer befindet
     *
     * @throws EventException Falls der User nicht gefunden wird
     */
    public void toggleMemberRole(User user, Group group) throws EventException {
        validationService.throwIfNotInGroup(group, user);

        Role role = group.getRoles().get(user.getId());
        updateRole(user, group, role.toggle());
    }


    // ################################# SINGLE EVENTS ###########################################
    // Spezifische Events werden erzeugt, validiert, auf die Gruppe angewandt und gespeichert


    //TODO: more validation
    private Group createGroup(User user, UUID parent, GroupType groupType, Visibility visibility, long userLimit) {
        Event event = new CreateGroupEvent(UUID.randomUUID(),
                                           user.getId(),
                                           parent,
                                           groupType, visibility,
                                           userLimit);
        Group group = ProjectionService.projectSingleGroup(Collections.singletonList(event));

        log.trace("Es wurde eine Gruppe erstellt. ({}, {})", visibility, group.getId());

        eventStoreService.saveEvent(event);

        return group;
    }

    //TODO: test if exception interrupts runtime
    public void addUser(User user, Group group) {
        validationService.throwIfUserAlreadyInGroup(group, user);

        Event event = new AddUserEvent(group, user);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }

    /**
     * Dasselbe wie addUser(), aber exceptions werden abgefangen und nicht geworfen.
     */
    private void addUserSilent(User user, Group group) {
        try {
            addUser(user, group);
        } catch (Exception e) {
            log.trace("Doppelter User wurde nicht hinzugefügt ({})!", user.getId());
        }
    }

    public void deleteUser(User user, Group group) throws EventException {
        validationService.throwIfNotInGroup(group, user);
        validationService.throwIfLastAdmin(user, group);

        Event event = new DeleteUserEvent(group, user);
        event.apply(group);

        eventStoreService.saveEvent(event);

        if (validationService.checkIfGroupEmpty(group)) {
            deleteGroup(user, group);
        }
    }

    public void deleteGroup(User user, Group group) {
        inviteService.destroyLink(group);

        log.trace("Eine Gruppe wurde gelöscht ({})", group.getId());

        Event event = new DeleteGroupEvent(group, user);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }

    //TODO: Validate title
    public void updateTitle(User user, Group group, String title) {
        Event event = new UpdateGroupTitleEvent(group, user, title);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }

    //TODO: Validate description
    public void updateDescription(User user, Group group, String description) {
        Event event = new UpdateGroupDescriptionEvent(group, user, description);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }

    public void updateRole(User user, Group group, Role role) {
        Event event = new UpdateRoleEvent(group, user, role);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }

    //TODO: Validate limit
    public void updateUserLimit(User user, Group group, long userLimit) {
        Event event = new UpdateUserLimitEvent(group, user, userLimit);
        event.apply(group);

        eventStoreService.saveEvent(event);
    }
}
