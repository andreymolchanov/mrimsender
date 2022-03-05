/* (C)2022 */
package ru.mail.jira.plugins.myteam.controller;

import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.mail.jira.plugins.myteam.controller.dto.IssueCreationSettingsDto;
import ru.mail.jira.plugins.myteam.service.IssueCreationSettingsService;
import ru.mail.jira.plugins.myteam.service.UserChatService;

@Controller
@Path("/issueCreation")
@Produces(MediaType.APPLICATION_JSON)
public class IssueCreationSettingsController {

  private final UserChatService userChatService;
  private final IssueCreationSettingsService issueCreationSettingsService;
  private final JiraAuthenticationContext jiraAuthenticationContext;
  private final GlobalPermissionManager globalPermissionManager;

  @ResponseStatus(value = HttpStatus.FORBIDDEN)
  @ExceptionHandler(PermissionException.class)
  public String noRightsHandleException(PermissionException e) {
    return e.getLocalizedMessage();
  }

  public IssueCreationSettingsController(
      IssueCreationSettingsService issueCreationSettingsService,
      UserChatService userChatService,
      @ComponentImport JiraAuthenticationContext jiraAuthenticationContext,
      @ComponentImport GlobalPermissionManager globalPermissionManager) {
    this.issueCreationSettingsService = issueCreationSettingsService;
    this.userChatService = userChatService;
    this.jiraAuthenticationContext = jiraAuthenticationContext;
    this.globalPermissionManager = globalPermissionManager;
  }

  @GET
  @Path("/settings/all")
  public List<IssueCreationSettingsDto> getAllChatsSettings() throws PermissionException {
    checkPermissions();
    return issueCreationSettingsService.getAllSettings();
  }

  @GET
  @Path("/settings/chats/{id}")
  public IssueCreationSettingsDto getChatSettings(@PathParam("id") final String chatId)
      throws PermissionException {
    checkPermissions(chatId);
    return issueCreationSettingsService.getSettingsByChatId(chatId).orElse(null);
  }

  @PUT
  @RequiresXsrfCheck
  @Path("/settings/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public IssueCreationSettingsDto updateChatSettings(
      @PathParam("id") final int id, final IssueCreationSettingsDto settings)
      throws PermissionException {
    IssueCreationSettingsDto originalSettings = issueCreationSettingsService.getSettings(id);
    checkPermissions(originalSettings.getChatId());
    return issueCreationSettingsService.updateSettings(id, settings);
  }

  private ApplicationUser checkPermissions() throws PermissionException {
    return checkPermissions(null);
  }

  private ApplicationUser checkPermissions(@Nullable String chatId) throws PermissionException {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();

    if (isJiraAdmin(user)
        || (chatId != null && userChatService.isChatAdmin(chatId, user.getEmailAddress()))) {
      return user;
    }
    throw new PermissionException();
  }

  private boolean isJiraAdmin(ApplicationUser user) {
    return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
  }
}
