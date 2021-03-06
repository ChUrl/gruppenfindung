package mops.gruppen2.infrastructure.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import mops.gruppen2.aspect.annotation.TraceMethodCalls;
import mops.gruppen2.domain.model.group.Group;
import mops.gruppen2.domain.model.group.User;
import mops.gruppen2.domain.model.group.wrapper.Description;
import mops.gruppen2.domain.model.group.wrapper.Limit;
import mops.gruppen2.domain.model.group.wrapper.Title;
import mops.gruppen2.domain.service.EventStoreService;
import mops.gruppen2.domain.service.GroupService;
import mops.gruppen2.domain.service.helper.FileHelper;
import mops.gruppen2.domain.service.helper.ValidationHelper;
import mops.gruppen2.infrastructure.GroupCache;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.UUID;

@SuppressWarnings("SameReturnValue")
@Log4j2
@TraceMethodCalls
@RequiredArgsConstructor
@Controller
@RequestMapping("/gruppen2")
public class GroupDetailsController {

    private final GroupCache groupCache;
    private final GroupService groupService;
    private final EventStoreService eventStoreService;

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping("/details/{id}")
    public String getDetailsPage(KeycloakAuthenticationToken token,
                                 Model model,
                                 @PathVariable("id") String groupId) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        // Parent Badge
        Group parent = Group.EMPTY();
        if (group.hasParent()) {
            parent = groupCache.group(group.getParent());
        }

        model.addAttribute("group", group);
        model.addAttribute("parent", parent);

        // Detailseite für nicht-Mitglieder
        if (!group.isMember(principal)) {
            return "preview";
        }

        return "details";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/join")
    public String postDetailsJoin(KeycloakAuthenticationToken token,
                                  @PathVariable("id") String groupId) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        if (group.isMember(principal)) {
            return "redirect:/gruppen2/details/" + groupId;
        }

        groupService.addMember(group, principal, principal, new User(token));

        return "redirect:/gruppen2/details/" + groupId;
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/leave")
    public String postDetailsLeave(KeycloakAuthenticationToken token,
                                   @PathVariable("id") String groupId) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        groupService.kickMember(group, principal, principal);

        return "redirect:/gruppen2";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping("details/{id}/history")
    public String getDetailsHistory(KeycloakAuthenticationToken token,
                                    Model model,
                                    @PathVariable("id") String groupId) {

        model.addAttribute("events",
                           eventStoreService.findGroupEvents(UUID.fromString(groupId)));

        return "log";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping(value = "details/{id}/export/history/plain", produces = "text/plain;charset=UTF-8")
    public void getDetailsExportHistoryPlain(HttpServletResponse response,
                                             @PathVariable("id") String groupId) {

        String filename = "eventlog-" + groupId + ".txt";

        response.setContentType("text/txt;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"" + filename + "\"");

        try {
            response.getWriter()
                    .write(FileHelper.payloadsToPlain(
                            eventStoreService.findGroupPayloads(UUID.fromString(groupId))));
        } catch (IOException e) {
            log.error("Payloads konnten nicht geschrieben werden.", e);
        }
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping(value = "details/{id}/export/history/sql", produces = "application/sql;charset=UTF-8")
    public void getDetailsExportHistorySql(HttpServletResponse response,
                                           @PathVariable("id") String groupId) {

        String filename = "data.sql";

        response.setContentType("application/sql;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"" + filename + "\"");

        try {
            response.getWriter()
                    .write(FileHelper.eventDTOsToSql(
                            eventStoreService.findGroupDTOs(UUID.fromString(groupId))));
        } catch (IOException e) {
            log.error("Payloads konnten nicht geschrieben werden.", e);
        }
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping(value = "details/{id}/export/members", produces = "text/csv;charset=UTF-8")
    public void getDetailsExportMembers(HttpServletResponse response,
                                        @PathVariable("id") String groupId) {

        String filename = "teilnehmer-" + groupId + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                           "attachment; filename=\"" + filename + "\"");

        try {
            response.getWriter()
                    .print(FileHelper.writeCsvUserList(groupCache.group(UUID.fromString(groupId)).getMembers()));
        } catch (IOException e) {
            log.error("Teilnehmerliste konnte nicht geschrieben werden.", e);
        }
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping("/details/{id}/edit")
    public String getDetailsEdit(KeycloakAuthenticationToken token,
                                 Model model,
                                 HttpServletRequest request,
                                 @PathVariable("id") String groupId) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        // Invite Link
        String actualURL = request.getRequestURL().toString();
        String serverURL = actualURL.substring(0, actualURL.indexOf("gruppen2/"));
        String link = serverURL + "gruppen2/join/" + group.getLink();

        ValidationHelper.throwIfNoAdmin(group, principal);

        model.addAttribute("group", group);
        model.addAttribute("link", link);

        return "edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/meta")
    public String postDetailsEditMeta(KeycloakAuthenticationToken token,
                                      @PathVariable("id") String groupId,
                                      @Valid Title title,
                                      @Valid Description description) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        groupService.setTitle(group, principal, title);
        groupService.setDescription(group, principal, description);

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/userlimit")
    public String postDetailsEditUserLimit(KeycloakAuthenticationToken token,
                                           @PathVariable("id") String groupId,
                                           @Valid Limit limit) {
        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        groupService.setLimit(group, principal, limit);

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed("ROLE_orga")
    @PostMapping("/details/{id}/edit/csv")
    public String postDetailsEditCsv(KeycloakAuthenticationToken token,
                                     @PathVariable("id") String groupId,
                                     @RequestParam(value = "file", required = false) MultipartFile file) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        groupService.addUsersToGroup(group, principal, FileHelper.readCsvFile(file));

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/role/{userid}")
    public String postDetailsEditRole(KeycloakAuthenticationToken token,
                                      @PathVariable("id") String groupId,
                                      @PathVariable("userid") String target) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        ValidationHelper.throwIfNoAdmin(group, principal);
        if (target.equals(principal)) {
            ValidationHelper.throwIfLastAdmin(group, principal);
        }

        groupService.toggleMemberRole(group, principal, target);

        // Falls sich der User selbst die Rechte genommen hat
        if (!ValidationHelper.checkIfAdmin(group, principal)) {
            return "redirect:/gruppen2/details/" + groupId;
        }

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/delete/{userid}")
    public String postDetailsEditDelete(KeycloakAuthenticationToken token,
                                        @PathVariable("id") String groupId,
                                        @PathVariable("userid") String target) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupId));

        ValidationHelper.throwIfNoAdmin(group, principal);

        // Der eingeloggte User kann sich nicht selbst entfernen (er kann aber verlassen)
        if (!principal.equals(target)) {
            groupService.kickMember(group, principal, target);
        }

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/destroy")
    public String postDetailsEditDestroy(KeycloakAuthenticationToken token,
                                         @PathVariable("id") String groupid) {

        String principal = token.getName();
        Group group = groupCache.group(UUID.fromString(groupid));

        groupService.deleteGroup(group, principal);

        return "redirect:/gruppen2";
    }

    //TODO: Method + view for /details/{id}/member/{id}
}
