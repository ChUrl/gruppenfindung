package mops.gruppen2.controller;


import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import mops.gruppen2.domain.Exceptions.EventException;
import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.apiWrapper.UpdatedGroupRequestMapper;
import mops.gruppen2.domain.event.Event;
import mops.gruppen2.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Ein Beispiel für eine API mit Swagger.
 */
@RestController
@RequestMapping("/gruppen2/api")
public class APIController {

    private final SerializationService serializationService;
    private final EventService eventService;
    private final GroupService groupService;

    public APIController(SerializationService serializationService, EventService eventService, GroupService groupService) {
        this.serializationService = serializationService;
        this.eventService = eventService;
        this.groupService = groupService;
    }

    @GetMapping("/updateGroups/{status}")
    @ApiOperation(value = "Gibt alle Gruppen zurück in denen sich etwas geändert hat")
    public UpdatedGroupRequestMapper updateGroup(@ApiParam("Letzter Status des Anfragestellers")  @PathVariable Long status) throws EventException {
        List<Event> events = eventService.getNewEvents(status);
        UpdatedGroupRequestMapper updatedGroupRequestMapper = APIFormatterService.wrapp(eventService.getMaxEvent_id(), groupService.projectEventList(events));

        return updatedGroupRequestMapper;
    }

    @GetMapping("/getGroupIdsOfUser/{teilnehmer}")
    @ApiOperation(value = "Gibt alle Gruppen zurück in denen sich ein Teilnehmer befindet")
    public List<Long> getGroupsOfUser(@ApiParam("Teilnehmer dessen groupIds zurückgegeben werden sollen")  @PathVariable String teilnehmer) throws EventException {
        return eventService.getGroupsOfUser(teilnehmer);
    }

    @GetMapping("/getGroup/{groupId}")
    @ApiOperation(value = "Gibt die Gruppe mit der als Parameter mitgegebenden groupId zurück")
    public Group getGroupFromId(@ApiParam("GruppenId der gefordeten Gruppe") @PathVariable Long groupId) throws EventException{
        List<Event> eventList = eventService.getEventsOfGroup(groupId);

        List<Group> groups = groupService.projectEventList(eventList);
        return groups.get(0);
    }

    @PostMapping(value = "/uploadcsv", consumes = "text/csv")
    public void uploadCsv(@RequestBody InputStream body) throws IOException {
        System.out.println(CsvService.read(body));
    }

    @PostMapping(value = "/uploadcsv", consumes = "multipart/form-data")
    public void uploadMultipart(@RequestParam("file") MultipartFile file) throws IOException {
        System.out.println(CsvService.read(file.getInputStream()));
    }
}
