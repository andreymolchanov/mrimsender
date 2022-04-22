/* (C)2021 */
package ru.mail.jira.plugins.myteam.rulesengine.rules.state.issuecreation;

import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.user.ApplicationUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import ru.mail.jira.plugins.myteam.configuration.createissue.customfields.CreateIssueFieldValueHandler;
import ru.mail.jira.plugins.myteam.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.myteam.dto.InlineKeyboardMarkupButton;
import ru.mail.jira.plugins.myteam.protocol.MessageFormatter;
import ru.mail.jira.plugins.myteam.protocol.events.ButtonClickEvent;
import ru.mail.jira.plugins.myteam.protocol.events.MyteamEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.RuleType;
import ru.mail.jira.plugins.myteam.rulesengine.models.ruletypes.StateActionRuleType;
import ru.mail.jira.plugins.myteam.rulesengine.rules.BaseRule;
import ru.mail.jira.plugins.myteam.rulesengine.states.base.BotState;
import ru.mail.jira.plugins.myteam.rulesengine.states.issuecreation.CreatingIssueState;
import ru.mail.jira.plugins.myteam.rulesengine.states.issuecreation.FillingIssueFieldState;
import ru.mail.jira.plugins.myteam.service.IssueCreationService;
import ru.mail.jira.plugins.myteam.service.RulesEngine;
import ru.mail.jira.plugins.myteam.service.UserChatService;

@Rule(
    name = "show issue creation message",
    description = "Calls to show filled and current filling fields")
public class ShowIssueCreationProgressRule extends BaseRule {

  static final RuleType NAME = StateActionRuleType.ShowCreatingIssueProgressMessage;

  private final IssueCreationService issueCreationService;

  public ShowIssueCreationProgressRule(
      UserChatService userChatService,
      RulesEngine rulesEngine,
      IssueCreationService issueCreationService) {
    super(userChatService, rulesEngine);
    this.issueCreationService = issueCreationService;
  }

  @Condition
  public boolean isValid(@Fact("state") BotState state, @Fact("command") String command) {
    return (state instanceof CreatingIssueState || state instanceof FillingIssueFieldState)
        && NAME.equalsName(command);
  }

  @Action
  public void execute(
      @Fact("event") MyteamEvent event,
      @Fact("state") BotState state,
      @Fact("args") String messagePrefix)
      throws MyteamServerErrorException, IOException, UserNotFoundException {
    ApplicationUser user = userChatService.getJiraUserFromUserChatId(event.getUserId());
    String chatId = event.getChatId();
    Locale locale = userChatService.getUserLocale(user);

    BotState prevState = userChatService.getPrevState(event.getChatId());

    CreatingIssueState issueCreationState =
        (CreatingIssueState) (state instanceof CreatingIssueState ? state : prevState);

    Optional<Field> field = issueCreationState.getCurrentField();

    if (!field.isPresent()) {
      userChatService.sendMessageText(
          event.getChatId(),
          userChatService.getRawText(
                  locale,
                  "ru.mail.jira.plugins.myteam.messageFormatter.createIssue.issueCreationConfirmation")
              + issueCreationState.createInsertFieldMessage(locale, ""),
          getIssueCreationConfirmButtons(locale));

    } else {
      CreateIssueFieldValueHandler handler = issueCreationService.getFieldValueHandler(field.get());

      FillingIssueFieldState fillingFieldState = null;

      if (state instanceof FillingIssueFieldState) {
        fillingFieldState = (FillingIssueFieldState) state;
      }

      String msg =
          issueCreationState.createInsertFieldMessage(
              locale,
              handler.getInsertFieldMessage(
                  issueCreationState.getProject(),
                  issueCreationState.getIssueType(),
                  fillingFieldState,
                  user,
                  locale));

      List<List<InlineKeyboardMarkupButton>> handlerButtons =
          handler.getButtons(
              issueCreationState.getProject(),
              issueCreationState.getIssueType(),
              fillingFieldState,
              user,
              locale);

      List<List<InlineKeyboardMarkupButton>> buttons =
          fillingFieldState != null && fillingFieldState.isAdditionalField()
              ? MessageFormatter.buildButtonsWithBack(
                  handlerButtons,
                  userChatService.getRawText(
                      locale,
                      "ru.mail.jira.plugins.myteam.mrimsenderEventListener.cancelButton.text"))
              : MessageFormatter.buildButtonsWithCancel(
                  handlerButtons,
                  userChatService.getRawText(
                      locale,
                      "ru.mail.jira.plugins.myteam.myteamEventsListener.cancelIssueCreationButton.text"));

      if (event instanceof ButtonClickEvent
          && fillingFieldState != null
          && fillingFieldState.isSearchOn()) {
        userChatService.editMessageText(
            chatId, ((ButtonClickEvent) event).getMsgId(), msg, buttons);
      } else {
        userChatService.sendMessageText(event.getChatId(), msg, buttons);
      }
    }

    if (event instanceof ButtonClickEvent) {
      userChatService.answerCallbackQuery(((ButtonClickEvent) event).getQueryId());
    }
  }

  private List<List<InlineKeyboardMarkupButton>> getIssueCreationConfirmButtons(Locale locale) {
    List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();
    List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
    buttons.add(buttonsRow);

    buttonsRow.add(
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            userChatService.getRawText(
                locale,
                "ru.mail.jira.plugins.myteam.mrimsenderEventListener.issueCreationConfirmButton.text"),
            StateActionRuleType.ConfirmIssueCreation.getName()));

    buttonsRow.add(
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            userChatService.getRawText(
                locale,
                "ru.mail.jira.plugins.myteam.mrimsenderEventListener.issueAddExtraFieldsButton.text"),
            StateActionRuleType.AddAdditionalFields.getName()));
    return MessageFormatter.buildButtonsWithCancel(
        buttons,
        userChatService.getRawText(
            locale,
            "ru.mail.jira.plugins.myteam.myteamEventsListener.cancelIssueCreationButton.text"));
  }
}
