/* (C)2022 */
package ru.mail.jira.plugins.myteam.rulesengine.rules.state.assignissue;

import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.user.ApplicationUser;
import java.io.IOException;
import java.util.Locale;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import ru.mail.jira.plugins.myteam.commons.Utils;
import ru.mail.jira.plugins.myteam.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.protocol.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.exceptions.AssigneeChangeValidationException;
import ru.mail.jira.plugins.myteam.rulesengine.rules.BaseRule;
import ru.mail.jira.plugins.myteam.rulesengine.states.AssigningIssueState;
import ru.mail.jira.plugins.myteam.rulesengine.states.base.BotState;
import ru.mail.jira.plugins.myteam.service.IssueService;
import ru.mail.jira.plugins.myteam.service.RulesEngine;
import ru.mail.jira.plugins.myteam.service.UserChatService;

@Rule(
    name = "user mention input result",
    description = "Fired when waiting for issue assignee on input")
public class AssignIssueInputRule extends BaseRule {

  private final IssueService issueService;

  public AssignIssueInputRule(
      UserChatService userChatService, RulesEngine rulesEngine, IssueService issueService) {
    super(userChatService, rulesEngine);
    this.issueService = issueService;
  }

  @Condition
  public boolean isValid(@Fact("state") BotState state, @Fact("args") String mention) {
    return state instanceof AssigningIssueState
        && (((AssigningIssueState) state).getIssueKey() != null)
        && mention != null
        && mention.length() > 0;
  }

  @Action
  public void execute(
      @Fact("event") ChatMessageEvent event,
      @Fact("state") AssigningIssueState state,
      @Fact("args") String userMention)
      throws MyteamServerErrorException, IOException {

    ApplicationUser user = userChatService.getJiraUserFromUserChatId(event.getChatId());
    Locale locale = userChatService.getUserLocale(user);

    String userEmail = Utils.getEmailFromMention(event);

    if (userEmail == null) {
      userEmail = userMention;
    }

    try {
      if (issueService.changeIssueAssignee(state.getIssueKey(), userEmail, user)) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getRawText(
                locale, "ru.mail.jira.plugins.myteam.messageQueueProcessor.assignIssue.success"));
        userChatService.deleteState(event.getChatId());
      } else {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getRawText(
                locale, "ru.mail.jira.plugins.myteam.messageQueueProcessor.assignIssue.error"),
            messageFormatter.getCancelButton(locale));
      }
    } catch (UserNotFoundException | AssigneeChangeValidationException e) {
      userChatService.sendMessageText(
          event.getChatId(),
          userChatService.getRawText(
              locale, "ru.mail.jira.plugins.myteam.messageQueueProcessor.assignIssue.error"),
          messageFormatter.getCancelButton(locale));
    }
  }
}
