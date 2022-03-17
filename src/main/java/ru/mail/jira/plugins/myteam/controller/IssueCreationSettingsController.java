/* (C)2022 */
package ru.mail.jira.plugins.myteam.controller;

import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.mail.jira.plugins.myteam.commons.PermissionHelperService;
import ru.mail.jira.plugins.myteam.controller.dto.IssueCreationSettingsDto;
import ru.mail.jira.plugins.myteam.service.IssueCreationSettingsService;

@Controller
@Path("/issueCreation")
@Produces(MediaType.APPLICATION_JSON)
public class IssueCreationSettingsController {

  private final IssueCreationSettingsService issueCreationSettingsService;
  private final JiraAuthenticationContext jiraAuthenticationContext;
  private final PermissionHelperService permissionHelperService;

  @ResponseStatus(value = HttpStatus.FORBIDDEN)
  @ExceptionHandler(PermissionException.class)
  public String noRightsHandleException(PermissionException e) {
    return e.getLocalizedMessage();
  }

  public IssueCreationSettingsController(
      IssueCreationSettingsService issueCreationSettingsService,
      PermissionHelperService permissionHelperService,
      @ComponentImport JiraAuthenticationContext jiraAuthenticationContext) {
    this.issueCreationSettingsService = issueCreationSettingsService;
    this.jiraAuthenticationContext = jiraAuthenticationContext;
    this.permissionHelperService = permissionHelperService;
  }

  @GET
  @Path("/settings/all")
  public List<IssueCreationSettingsDto> getAllChatsSettings() throws PermissionException {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
    permissionHelperService.checkChatAdminPermissions(user);
    return issueCreationSettingsService.getAllSettings();
  }

  @GET
  @Path("/settings/{id}")
  public IssueCreationSettingsDto getChatSettingsById(@PathParam("id") final Integer id)
      throws PermissionException {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
    IssueCreationSettingsDto settings = issueCreationSettingsService.getSettingsById(id);
    permissionHelperService.checkChatAdminPermissions(user, settings.getChatId());
    return issueCreationSettingsService.getSettingsById(id);
  }

  @GET
  @Path("/settings/chats/{id}")
  public IssueCreationSettingsDto getChatSettings(@PathParam("id") final String id)
      throws PermissionException {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
    permissionHelperService.checkChatAdminPermissions(user, id);
    return issueCreationSettingsService.getSettingsByChatId(id).orElse(null);
  }

  @GET
  @Path("/projects/{id}/settings")
  public List<IssueCreationSettingsDto> getProjectChatSettings(
      @PathParam("id") final Long projectId) throws PermissionException {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
    permissionHelperService.checkProjectPermissions(user, projectId);
    return issueCreationSettingsService.getSettingsByProjectId(projectId).stream()
        .peek(
            s -> s.setCanEdit(permissionHelperService.isChatAdminOrJiraAdmin(s.getChatId(), user)))
        .collect(Collectors.toList());
  }

  @PUT
  @RequiresXsrfCheck
  @Path("/settings/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public IssueCreationSettingsDto updateChatSettings(
      @PathParam("id") final int id, final IssueCreationSettingsDto settings)
      throws PermissionException {
    IssueCreationSettingsDto originalSettings = issueCreationSettingsService.getSettings(id);
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();
    permissionHelperService.checkChatAdminPermissions(user, originalSettings.getChatId());
    return issueCreationSettingsService.updateSettings(id, settings);
  }
}
