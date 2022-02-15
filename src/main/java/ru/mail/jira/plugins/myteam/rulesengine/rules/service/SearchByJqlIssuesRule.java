/* (C)2021 */
package ru.mail.jira.plugins.myteam.rulesengine.rules.service;

import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.user.ApplicationUser;
import java.io.IOException;
import java.util.Locale;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import ru.mail.jira.plugins.myteam.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.protocol.MessageFormatter;
import ru.mail.jira.plugins.myteam.protocol.events.ButtonClickEvent;
import ru.mail.jira.plugins.myteam.protocol.events.MyteamEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.CommandRuleType;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.RuleType;
import ru.mail.jira.plugins.myteam.rulesengine.rules.BaseRule;
import ru.mail.jira.plugins.myteam.rulesengine.states.JqlSearchState;
import ru.mail.jira.plugins.myteam.service.IssueService;
import ru.mail.jira.plugins.myteam.service.RulesEngine;
import ru.mail.jira.plugins.myteam.service.UserChatService;

@Rule(name = "jql search", description = "Shows issues by JQL")
public class SearchByJqlIssuesRule extends BaseRule {
  static final RuleType NAME = CommandRuleType.SearchByJql;
  private final IssueService issueService;

  public SearchByJqlIssuesRule(
      UserChatService userChatService, RulesEngine rulesEngine, IssueService issueService) {
    super(userChatService, rulesEngine);
    this.issueService = issueService;
  }

  @Condition
  public boolean isValid(@Fact("command") String command) {
    return NAME.equalsName(command);
  }

  @Action
  public void execute(@Fact("event") MyteamEvent event, @Fact("args") String jql)
      throws UserNotFoundException, MyteamServerErrorException, IOException {
    JqlSearchState newState = new JqlSearchState(userChatService, issueService, jql);

    if (jql == null || jql.length() == 0) { // if jql is not provided ask for input
      ApplicationUser user = userChatService.getJiraUserFromUserChatId(event.getUserId());
      String chatId = event.getChatId();
      Locale locale = userChatService.getUserLocale(user);

      userChatService.answerCallbackQuery(((ButtonClickEvent) event).getQueryId());
      userChatService.sendMessageText(
          chatId,
          userChatService.getRawText(
              locale,
              "ru.mail.jira.plugins.myteam.myteamEventsListener.searchByJqlClauseButton.insertJqlClause.message"),
          MessageFormatter.buildButtonsWithCancel(
              null,
              userChatService.getRawText(
                  locale,
                  "ru.mail.jira.plugins.myteam.mrimsenderEventListener.cancelButton.text")));

      newState.setWaiting(true);

    } else {
      newState.setWaiting(false);
      newState.updatePage(event, false);
    }
    userChatService.setState(event.getChatId(), newState);
  }
}
