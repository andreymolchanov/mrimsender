/* (C)2021 */
package ru.mail.jira.plugins.myteam.service.impl;

import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import org.jeasy.rules.api.Fact;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.RulesEngineParameters;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.myteam.accessrequest.service.AccessRequestService;
import ru.mail.jira.plugins.myteam.bot.events.MyteamEvent;
import ru.mail.jira.plugins.myteam.bot.rulesengine.core.RulesEngine;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.RuleEngineFiringException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.ErrorRuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.RuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.buttons.*;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.ChatIdCommandRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.HelpCommandRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.MenuCommandRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.PinMessageCommandRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.admin.IssueCreationSettingsCommand;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.issue.*;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.issue.editing.IssueTransitionRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.issue.editing.IssueTransitionSelectRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.service.CommentIssueButtonsService;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.service.CommonButtonsService;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.errors.IssueNoPermissionErrorRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.errors.IssueNotFoundErrorRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.errors.UnknownErrorRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.service.CommentIssueCommandBotRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.service.CreateIssueByReplyRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.service.DefaultMessageRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.service.SearchByJqlIssuesRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.CommentingIssueFromGroupChatRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.assignissue.AssignIssueInputRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.issuecomment.IssueCommentInputRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.issuecreation.*;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.issuesearch.IssueKeyInputRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.state.jqlsearch.JqlInputRule;
import ru.mail.jira.plugins.myteam.bot.rulesengine.states.base.BotState;
import ru.mail.jira.plugins.myteam.bot.rulesengine.states.base.EmptyState;
import ru.mail.jira.plugins.myteam.component.EventMessagesTextConverter;
import ru.mail.jira.plugins.myteam.component.JiraMarkdownToChatMarkdownConverter;
import ru.mail.jira.plugins.myteam.component.PermissionHelper;
import ru.mail.jira.plugins.myteam.db.repository.MyteamChatRepository;
import ru.mail.jira.plugins.myteam.myteam.MyteamApiClient;
import ru.mail.jira.plugins.myteam.myteam.dto.ChatType;
import ru.mail.jira.plugins.myteam.protocol.MyteamService;
import ru.mail.jira.plugins.myteam.service.*;

@Component
@ExportAsService(LifecycleAware.class)
public class RulesEngineImpl
    implements ru.mail.jira.plugins.myteam.service.RulesEngine, LifecycleAware {

  private final RulesEngine commandsRuleEngine;
  private final RulesEngine errorsRuleEngine;
  private final RulesEngine stateActionsRuleEngine;

  private final CommonButtonsService commonButtonsService;
  private final IssueCreationService issueCreationService;
  private final UserChatService userChatService;
  private final IssueService issueService;
  private final IssueCreationSettingsService issueCreationSettingsService;
  private final ReminderService reminderService;
  private final AccessRequestService accessRequestService;
  private final EventMessagesTextConverter eventMessagesTextConverter;
  private final MyteamApiClient myteamApiClient;

  private final MyteamChatRepository myteamChatRepository;
  private final JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter;
  private final PermissionHelper permissionHelper;

  private final MyteamService myteamService;

  private final UserSearchService userSearchService;

  public RulesEngineImpl(
      CommonButtonsService commonButtonsService,
      IssueCreationService issueCreationService,
      UserChatService userChatService,
      IssueService issueService,
      IssueCreationSettingsService issueCreationSettingsService,
      ReminderService reminderService,
      AccessRequestService accessRequestService,
      EventMessagesTextConverter eventMessagesTextConverter,
      MyteamApiClient myteamApiClient,
      MyteamChatRepository myteamChatRepository,
      JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter,
      @Lazy MyteamService myteamService,
      PermissionHelper permissionHelper,
      @ComponentImport UserSearchService userSearchService) {
    this.commonButtonsService = commonButtonsService;
    this.issueCreationService = issueCreationService;
    this.userChatService = userChatService;
    this.issueService = issueService;
    this.issueCreationSettingsService = issueCreationSettingsService;
    this.reminderService = reminderService;
    this.accessRequestService = accessRequestService;
    this.eventMessagesTextConverter = eventMessagesTextConverter;
    this.myteamApiClient = myteamApiClient;
    this.myteamChatRepository = myteamChatRepository;
    this.jiraMarkdownToChatMarkdownConverter = jiraMarkdownToChatMarkdownConverter;
    this.myteamService = myteamService;
    this.permissionHelper = permissionHelper;
    this.userSearchService = userSearchService;

    RulesEngineParameters engineParams =
        new RulesEngineParameters(
            true, false, false, RulesEngineParameters.DEFAULT_RULE_PRIORITY_THRESHOLD);
    this.commandsRuleEngine = new RulesEngine(engineParams, userChatService);
    this.errorsRuleEngine = new RulesEngine(engineParams, userChatService);
    this.stateActionsRuleEngine = new RulesEngine(engineParams, userChatService);
  }

  @Override
  public void onStart() {
    // Defaults
    stateActionsRuleEngine.registerRule(new DefaultMessageRule(userChatService, this));

    // Buttons
    commandsRuleEngine.registerRule(new SearchIssueByJqlInputRule(userChatService, this));
    commandsRuleEngine.registerRule(new SearchIssueByKeyInputRule(userChatService, this));
    commandsRuleEngine.registerRule(new NextPageRule(userChatService, this));
    commandsRuleEngine.registerRule(new PrevPageRule(userChatService, this));
    commandsRuleEngine.registerRule(new RevertRule(userChatService, this));
    commandsRuleEngine.registerRule(new CancelRule(userChatService, this));
    commandsRuleEngine.registerRule(new CommentIssueRule(userChatService, this));
    commandsRuleEngine.registerRule(
        new CreateIssueRule(userChatService, this, issueService, issueCreationService));
    commandsRuleEngine.registerRule(
        new ViewCommentsRule(
            userChatService, this, issueService, jiraMarkdownToChatMarkdownConverter));
    commandsRuleEngine.registerRule(new CommentingIssueFromGroupChatRule(userChatService, this));
    commandsRuleEngine.registerRule(
        new ReplyRule(userChatService, this, accessRequestService, issueService));
    // Admin Group Commands
    commandsRuleEngine.registerRule(
        new IssueCreationSettingsCommand(userChatService, this, issueService));

    // Commands
    commandsRuleEngine.registerRule(new ChatIdCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(new HelpCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(new MenuCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(new WatchingIssuesCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(new AssignedIssuesCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(new CreatedIssuesCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(
        new LinkIssueWithChatCommandRule(userChatService, this, myteamService));
    commandsRuleEngine.registerRule(new AssignIssueCommandRule(userChatService, this));
    commandsRuleEngine.registerRule(
        new ViewIssueCommandRule(userChatService, this, commonButtonsService, issueService));
    commandsRuleEngine.registerRule(
        new IssueRemindCommandRule(userChatService, this, reminderService));
    commandsRuleEngine.registerRule(new WatchIssueCommandRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(new IssueTransitionRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(
        new IssueTransitionSelectRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(
        new UnwatchIssueCommandRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(
        new FieldValueEditRule(userChatService, this, issueCreationService));
    commandsRuleEngine.registerRule(
        new FieldValueSelectRule(userChatService, this, issueCreationService));
    commandsRuleEngine.registerRule(
        new CommentIssueCommandBotRule(
            userChatService,
            this,
            myteamApiClient,
            issueService,
            eventMessagesTextConverter,
            myteamChatRepository,
            new CommentIssueButtonsService(userChatService)));
    commandsRuleEngine.registerRule(
        new PinMessageCommandRule(userChatService, this, myteamApiClient));
    commandsRuleEngine.registerRule(
        new AddWatchersToIssueCommandRule(
            userChatService, this, issueService, myteamApiClient, userSearchService));
    commandsRuleEngine.registerRule(
        new AddAttachmentsToIssueCommandRule(userChatService, this, issueService));

    // Service
    commandsRuleEngine.registerRule(new SearchByJqlIssuesRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(
        new CreateIssueByReplyRule(
            userChatService,
            this,
            issueCreationSettingsService,
            issueCreationService,
            issueService,
            eventMessagesTextConverter,
            permissionHelper));

    // States
    stateActionsRuleEngine.registerRule(new JqlInputRule(userChatService, this));
    stateActionsRuleEngine.registerRule(
        new ProjectKeyInputRule(userChatService, this, issueService));
    stateActionsRuleEngine.registerRule(
        new IssueCommentInputRule(userChatService, this, issueService));
    stateActionsRuleEngine.registerRule(new FieldInputRule(userChatService, this));
    stateActionsRuleEngine.registerRule(new IssueKeyInputRule(userChatService, this));
    stateActionsRuleEngine.registerRule(
        new AssignIssueInputRule(userChatService, this, issueService));

    commandsRuleEngine.registerRule(
        new IssueTypeSelectButtonRule(userChatService, this, issueService));
    commandsRuleEngine.registerRule(
        new ShowIssueCreationProgressRule(userChatService, this, issueCreationService));
    commandsRuleEngine.registerRule(
        new ConfirmIssueCreationRule(userChatService, this, issueCreationService));
    commandsRuleEngine.registerRule(
        new AddAdditionalFieldsRule(userChatService, this, issueCreationService));
    commandsRuleEngine.registerRule(
        new SelectAdditionalFieldRule(userChatService, this, issueCreationService));

    // Errors
    errorsRuleEngine.registerRule(new UnknownErrorRule(userChatService, this));
    errorsRuleEngine.registerRule(new IssueNotFoundErrorRule(userChatService, this));
    errorsRuleEngine.registerRule(new IssueNoPermissionErrorRule(userChatService, this));
  }

  @Override
  public void onStop() {}

  @Override
  public void fireCommand(RuleType command, MyteamEvent event) {
    fireCommand(command.getName(), event, "");
  }

  @Override
  public void fireCommand(RuleType command, MyteamEvent event, String args) {
    fireCommand(command.getName(), event, args);
  }

  @Override
  public void fireCommand(String command, MyteamEvent event, String args) {
    Facts facts = formBasicFacts(event, args);
    facts.put("command", command);
    commandsRuleEngine.fire(facts);
  }

  @Override
  public void fireStateAction(MyteamEvent event, String args) {
    Facts facts = formBasicFacts(event, args);
    stateActionsRuleEngine.fire(facts);
  }

  @Override
  public void fireError(ErrorRuleType errorType, MyteamEvent event, @Nullable Exception e) {
    Facts facts = new Facts();
    facts.add(new Fact<>("error", errorType));
    facts.add(new Fact<>("event", event));
    if (e != null) {
      facts.add(new Fact<>("exception", e));
    }
    errorsRuleEngine.fire(facts);
  }

  @Override
  public void fireError(ErrorRuleType errorType, MyteamEvent event, String msg) {
    fireError(errorType, event, new RuleEngineFiringException(msg));
  }

  private Facts formBasicFacts(MyteamEvent event, String args) {
    Facts facts = new Facts();

    BotState state = userChatService.getState(event.getChatId());
    BotState prevState = userChatService.getPrevState(event.getChatId());

    facts.put("args", args == null ? "" : args);
    facts.add(new Fact<>("event", event));
    facts.add(new Fact<>("state", state == null ? new EmptyState() : state));
    facts.add(new Fact<>("prevState", prevState == null ? new EmptyState() : prevState));
    facts.add(new Fact<>("isGroup", event.getChatType() == ChatType.GROUP));
    return facts;
  }
}
