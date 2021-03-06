package mops.gruppen2.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.domain.exception.BadPayloadException;
import mops.gruppen2.domain.service.helper.CommonHelper;
import mops.gruppen2.domain.service.helper.FileHelper;
import mops.gruppen2.persistance.EventRepository;
import mops.gruppen2.persistance.dto.EventDTO;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class EventStoreService {

    private final EventRepository eventStore;


    //########################################### SAVE ###########################################


    /**
     * Erzeugt ein DTO aus einem Event und speicher es.
     *
     * @param event Event, welches gespeichert wird
     */
    public void saveEvent(Event event) {
        eventStore.save(getDTOFromEvent(event));
    }

    public void saveAll(Event... events) {
        for (Event event : events) {
            eventStore.save(getDTOFromEvent(event));
        }
    }


    //########################################### DTOs ###########################################


    /**
     * Erzeugt aus einem Event Objekt ein EventDTO Objekt.
     *
     * @param event Event, welches in DTO übersetzt wird
     *
     * @return EventDTO (Neues DTO)
     */
    private static EventDTO getDTOFromEvent(Event event) {
        try {
            String payload = FileHelper.serializeEventJson(event);
            return new EventDTO(null,
                                event.getGroupid().toString(),
                                event.getVersion(),
                                event.getExec(),
                                event.getTarget(),
                                Timestamp.valueOf(event.getDate()),
                                payload);
        } catch (JsonProcessingException e) {
            log.error("Event ({}) konnte nicht serialisiert werden!", event, e);
            throw new BadPayloadException(EventStoreService.class.toString());
        }
    }

    /**
     * Erzeugt aus einer Liste von eventDTOs eine Liste von Events.
     *
     * @param eventDTOS Liste von DTOs
     *
     * @return Liste von Events
     */
    private static List<Event> getEventsFromDTOs(List<EventDTO> eventDTOS) {
        return eventDTOS.stream()
                        .map(EventStoreService::getEventFromDTO)
                        .collect(Collectors.toList());
    }

    private static Event getEventFromDTO(EventDTO dto) {
        try {
            return FileHelper.deserializeEventJson(dto.getEvent_payload());
        } catch (JsonProcessingException e) {
            log.error("Payload {} konnte nicht deserialisiert werden!", dto.getEvent_payload(), e);
            throw new BadPayloadException(EventStoreService.class.toString());
        }
    }


    // #################################### SIMPLE QUERIES #######################################


    public List<Event> findAllEvents() {
        return getEventsFromDTOs(eventStore.findAllEvents());
    }

    public List<Event> findGroupEvents(UUID groupId) {
        return getEventsFromDTOs(eventStore.findGroupEvents(groupId.toString()));
    }

    public List<Event> findGroupEvents(List<UUID> ids) {
        return ids.stream()
                  .map(id -> eventStore.findGroupEvents(id.toString()))
                  .map(EventStoreService::getEventsFromDTOs)
                  .flatMap(Collection::stream)
                  .collect(Collectors.toList());
    }

    public List<String> findGroupPayloads(UUID groupId) {
        return eventStore.findGroupPayloads(groupId.toString());
    }

    public List<EventDTO> findGroupDTOs(UUID groupid) {
        return eventStore.findGroupEvents(groupid.toString());
    }

    public List<UUID> findChangedGroups(long eventid) {
        return CommonHelper.stringsToUUID(eventStore.findChangedGroupIds(eventid));
    }

    public long findMaxEventId() {
        return eventStore.findMaxEventId();
    }
}
