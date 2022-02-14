/* (C)2022 */
package ru.mail.jira.plugins.myteam.actions;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import ru.mail.jira.plugins.myteam.service.UserChatService;

public class ChatSettingsAction extends JiraWebActionSupport {
  private static final String SECURITY_BREACH = "securitybreach";
  private final JiraAuthenticationContext jiraAuthenticationContext;
  private final GlobalPermissionManager globalPermissionManager;
  private final UserChatService userChatService;

  public ChatSettingsAction(
      @ComponentImport JiraAuthenticationContext jiraAuthenticationContext,
      @ComponentImport GlobalPermissionManager globalPermissionManager,
      UserChatService userChatService) {
    this.jiraAuthenticationContext = jiraAuthenticationContext;
    this.globalPermissionManager = globalPermissionManager;
    this.userChatService = userChatService;
  }

  @Override
  public String execute() {
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser();

    if (isJiraAdmin(user)) {
      return SUCCESS;
    }
    String chatId = this.getHttpRequest().getParameter("chatId");

    if (chatId != null) {
      if (!userChatService.isChatAdmin(chatId, user.getEmailAddress())) return SECURITY_BREACH;
    }
    return SUCCESS;
  }

  private boolean isJiraAdmin(ApplicationUser user) {
    return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
  }
}
