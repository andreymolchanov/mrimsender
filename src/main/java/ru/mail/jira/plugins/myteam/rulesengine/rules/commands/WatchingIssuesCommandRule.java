/* (C)2021 */
package ru.mail.jira.plugins.myteam.rulesengine.rules.commands;

import com.atlassian.jira.user.ApplicationUser;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import ru.mail.jira.plugins.myteam.protocol.events.MyteamEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.BaseRule;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.CommandRuleType;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.RuleType;
import ru.mail.jira.plugins.myteam.rulesengine.service.RulesEngine;
import ru.mail.jira.plugins.myteam.rulesengine.service.UserChatService;

@Rule(name = "/watching", description = "Shows user's active watching issues")
public class WatchingIssuesCommandRule extends BaseRule {
  static final RuleType NAME = CommandRuleType.WatchingIssues;

  public WatchingIssuesCommandRule(UserChatService userChatService, RulesEngine rulesEngine) {
    super(userChatService, rulesEngine);
  }

  @Condition
  public boolean isValid(@Fact("command") String command) {
    return NAME.equalsName(command);
  }

  @Action
  public void execute(@Fact("event") MyteamEvent event) {
    ApplicationUser user = userChatService.getJiraUserFromUserChatId(event.getUserId());
    if (user != null) {
      rulesEngine.fireCommand(
          CommandRuleType.SearchByJql,
          event,
          "watcher = currentUser() AND resolution = Unresolved ORDER BY updated");
    }
  }
}