/* (C)2020 */
package ru.mail.jira.plugins.myteam.component;

import com.atlassian.jira.exception.IssueNotFoundException;
import com.atlassian.jira.exception.NotFoundException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.commons.RestFieldException;
import ru.mail.jira.plugins.myteam.commons.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.db.model.FilterSubscription;
import ru.mail.jira.plugins.myteam.myteam.MyteamApiClient;
import ru.mail.jira.plugins.myteam.myteam.dto.response.AdminsResponse;

@Slf4j
@Component
public class PermissionHelper {

  private final GlobalPermissionManager globalPermissionManager;
  private final IssueManager issueManager;
  private final PermissionManager permissionManager;
  private final ProjectManager projectManager;
  private final UserData userData;
  private final MyteamApiClient myteamClient;

  public PermissionHelper(
      @ComponentImport GlobalPermissionManager globalPermissionManager,
      @ComponentImport IssueManager issueManager,
      @ComponentImport PermissionManager permissionManager,
      @ComponentImport ProjectManager projectManager,
      UserData userData,
      MyteamApiClient myteamClient) {
    this.globalPermissionManager = globalPermissionManager;
    this.issueManager = issueManager;
    this.permissionManager = permissionManager;
    this.projectManager = projectManager;
    this.userData = userData;
    this.myteamClient = myteamClient;
  }

  public boolean isChatAdminOrJiraAdmin(String chatId, @Nullable String userId) {
    if (userId == null) {
      return false;
    }
    return isChatAdminOrJiraAdmin(chatId, userData.getUserByMrimLogin(userId));
  }

  public boolean isChatAdminOrJiraAdmin(String chatId, @Nullable ApplicationUser user) {
    if (user == null) {
      return false;
    }
    if (isJiraAdmin(user)) {
      return true;
    }
    return isChatAdmin(chatId, user.getEmailAddress());
  }

  public boolean isChatAdmin(String chatId, String userId) {
    try {
      AdminsResponse response = myteamClient.getAdmins(chatId).getBody();
      return response.getAdmins() != null
          && response.getAdmins().stream().anyMatch(admin -> userId.equals(admin.getUserId()));
    } catch (MyteamServerErrorException e) {
      log.error("Unable to get chat admins", e);
      return false;
    }
  }

  public void checkIfProjectAdmin(ApplicationUser user, Project project) {
    if (!isProjectAdmin(user, project)) {
      throw new SecurityException();
    }
  }

  public boolean isJiraAdmin(ApplicationUser user) {
    return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
  }

  public boolean isProjectAdmin(ApplicationUser user, long projectId) {
    try {
      return isProjectAdmin(user, getExistingProject(projectId));
    } catch (Exception e) {
      log.error("calling isProjectAdmin({},{})", user, projectId, e);
      return false;
    }
  }

  public boolean isProjectAdmin(ApplicationUser user, String projectKey) {
    try {
      return isProjectAdmin(user, getExistingProject(projectKey));
    } catch (Exception e) {
      log.error("calling isProjectAdmin({},{})", user, projectKey, e);
      return false;
    }
  }

  public boolean isProjectAdmin(ApplicationUser user, Project project) {
    return isJiraAdmin(user)
        || permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, user);
  }

  private Project getExistingProject(long projectId) {
    Project project = projectManager.getProjectObj(projectId);
    if (project == null) {
      throw new RestFieldException("Project doesn't exist");
    }
    return project;
  }

  private Project getExistingProject(String projectKey) {
    Project project = projectManager.getProjectObjByKey(projectKey);
    if (project == null) {
      throw new RestFieldException("Project doesn't exist");
    }
    return project;
  }

  public ApplicationUser checkChatAdminPermissions(ApplicationUser user)
      throws PermissionException {
    return checkChatAdminPermissions(user, null);
  }

  public ApplicationUser checkChatAdminPermissions(ApplicationUser user, @Nullable String chatId)
      throws PermissionException {
    if (chatId != null && isChatAdminOrJiraAdmin(chatId, user)) {
      return user;
    }
    throw new PermissionException();
  }

  public ApplicationUser checkProjectPermissions(ApplicationUser user, String projectKey)
      throws PermissionException {

    if (isProjectAdmin(user, projectKey)) {
      return user;
    }
    throw new PermissionException();
  }

  public ApplicationUser checkProjectPermissions(ApplicationUser user, long projectId)
      throws PermissionException {

    if (isProjectAdmin(user, getExistingProject(projectId))) {
      return user;
    }
    throw new PermissionException();
  }

  public void checkSubscriptionPermission(
      ApplicationUser user, FilterSubscription filterSubscription) {
    if (!isJiraAdmin(user) && !filterSubscription.getUserKey().equals(user.getKey())) {
      throw new SecurityException();
    }
  }

  public boolean checkCreateIssuePermission(
      @NotNull final ApplicationUser applicationUser, @NotNull final String projectKey) {
    Project project = projectManager.getProjectObjByKey(projectKey);
    if (project == null) {
      throw new NotFoundException(String.format("Not found project by key %s", projectKey));
    }
    return permissionManager.hasPermission(
        ProjectPermissions.CREATE_ISSUES, project, applicationUser);
  }

  public boolean checkBrowseIssuePermission(
      @NotNull final ApplicationUser applicationUser, @Nullable final String issueKey) {
    Issue issue = issueManager.getIssueByKeyIgnoreCase(issueKey);
    if (Objects.isNull(issue)) {
      throw new IssueNotFoundException(String.format("Issue %s not found", issueKey));
    }
    return permissionManager.hasPermission(
        ProjectPermissions.BROWSE_PROJECTS, issue, applicationUser);
  }
}
