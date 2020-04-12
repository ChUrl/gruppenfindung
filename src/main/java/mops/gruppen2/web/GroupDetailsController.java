package mops.gruppen2.web;

import lombok.extern.log4j.Log4j2;
import mops.gruppen2.aspect.annotation.TraceMethodCalls;
import mops.gruppen2.domain.Group;
import mops.gruppen2.domain.User;
import mops.gruppen2.domain.service.CsvService;
import mops.gruppen2.domain.service.GroupService;
import mops.gruppen2.domain.service.IdService;
import mops.gruppen2.domain.service.InviteService;
import mops.gruppen2.domain.service.ProjectionService;
import mops.gruppen2.domain.service.ValidationService;
import mops.gruppen2.web.form.MetaForm;
import mops.gruppen2.web.form.UserLimitForm;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.cache.annotation.CacheEvict;
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
import javax.validation.Valid;
import java.util.UUID;

@SuppressWarnings("SameReturnValue")
@Log4j2
@TraceMethodCalls
@Controller
@RequestMapping("/gruppen2")
public class GroupDetailsController {

    private final InviteService inviteService;
    private final GroupService groupService;
    private final ProjectionService projectionService;

    public GroupDetailsController(InviteService inviteService, GroupService groupService, ProjectionService projectionService) {
        this.inviteService = inviteService;
        this.groupService = groupService;
        this.projectionService = projectionService;

    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping("/details/{id}")
    public String getDetailsPage(KeycloakAuthenticationToken token,
                                 Model model,
                                 @PathVariable("id") String groupId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        // Parent Badge
        UUID parentId = group.getParent();
        Group parent = projectionService.projectParent(parentId);

        model.addAttribute("group", group);
        model.addAttribute("parent", parent);

        // Detailseite für nicht-Mitglieder
        if (!ValidationService.checkIfMember(group, user)) {
            return "preview";
        }

        return "details";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/join")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsJoin(KeycloakAuthenticationToken token,
                                  @PathVariable("id") String groupId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        groupService.addUser(user, group);

        return "redirect:/gruppen2/details/" + groupId;
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/leave")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsLeave(KeycloakAuthenticationToken token,
                                   @PathVariable("id") String groupId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        groupService.deleteUser(user, group);

        return "redirect:/gruppen2";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @GetMapping("/details/{id}/edit")
    public String getDetailsMembers(KeycloakAuthenticationToken token,
                                    Model model,
                                    HttpServletRequest request,
                                    @PathVariable("id") String groupId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        // Invite Link
        String actualURL = request.getRequestURL().toString();
        String serverURL = actualURL.substring(0, actualURL.indexOf("gruppen2/"));
        String link = serverURL + "gruppen2/join/" + inviteService.getLinkByGroup(group);

        ValidationService.throwIfNoAdmin(group, user);

        model.addAttribute("group", group);
        model.addAttribute("link", link);

        return "edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/meta")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsMetaUpdate(KeycloakAuthenticationToken token,
                                        @PathVariable("id") String groupId,
                                        @Valid MetaForm form) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        groupService.updateTitle(user, group, form.getTitle());
        groupService.updateDescription(user, group, form.getDescription());

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/userlimit")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsMembersUpdateUserLimit(KeycloakAuthenticationToken token,
                                                    @PathVariable("id") String groupId,
                                                    @Valid UserLimitForm form) {
        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        groupService.updateUserLimit(user, group, form.getUserlimit());

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed("ROLE_orga")
    @PostMapping("/details/{id}/edit/csv")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsMembersUpdateCsv(KeycloakAuthenticationToken token,
                                              @PathVariable("id") String groupId,
                                              @RequestParam(value = "file", required = false) MultipartFile file) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(IdService.stringToUUID(groupId));

        groupService.addUsersToGroup(CsvService.readCsvFile(file), group, user);

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/role/{userid}")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsMembersUpdateRole(KeycloakAuthenticationToken token,
                                               @PathVariable("id") String groupId,
                                               @PathVariable("userid") String userId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        ValidationService.throwIfNoAdmin(group, user);

        groupService.toggleMemberRole(new User(userId), group);

        // Falls sich der User selbst die Rechte genommen hat
        if (!ValidationService.checkIfAdmin(group, user)) {
            return "redirect:/gruppen2/details/" + groupId;
        }

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/delete/{userid}")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsMembersDelete(KeycloakAuthenticationToken token,
                                           @PathVariable("id") String groupId,
                                           @PathVariable("userid") String userId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        ValidationService.throwIfNoAdmin(group, user);

        // Der eingeloggte User kann sich nicht selbst entfernen (er kann aber verlassen)
        if (!userId.equals(user.getId())) {
            groupService.deleteUser(new User(userId), group);
        }

        return "redirect:/gruppen2/details/" + groupId + "/edit";
    }

    @RolesAllowed({"ROLE_orga", "ROLE_studentin"})
    @PostMapping("/details/{id}/edit/destroy")
    @CacheEvict(value = "groups", allEntries = true)
    public String postDetailsDestroy(KeycloakAuthenticationToken token,
                                     @PathVariable("id") String groupId) {

        User user = new User(token);
        Group group = projectionService.projectSingleGroup(UUID.fromString(groupId));

        groupService.deleteGroup(user, group);

        return "redirect:/gruppen2";
    }

    //TODO: Method + view for /details/{id}/member/{id}
}