/* (C)2024 */
package ru.mail.jira.plugins.myteam.bot.rulesengine.rules.commands.issue;

import com.atlassian.jira.exception.IssueNotFoundException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.JiraKeyUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import ru.mail.jira.plugins.myteam.bot.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.CommandRuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.ErrorRuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.BaseRule;
import ru.mail.jira.plugins.myteam.commons.Utils;
import ru.mail.jira.plugins.myteam.commons.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.component.IssueTextConverter;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.File;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.Forward;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.Part;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.Reply;
import ru.mail.jira.plugins.myteam.service.IssueService;
import ru.mail.jira.plugins.myteam.service.RulesEngine;
import ru.mail.jira.plugins.myteam.service.UserChatService;

@Rule(name = "/addAttachment", description = "add to watchers issues passed users")
public class AddAttachmentsToIssueCommandRule extends BaseRule {
  private static final String NAME_LOWER_CASE =
      CommandRuleType.AddAttachment.getName().toLowerCase();
  private final IssueService issueService;

  public AddAttachmentsToIssueCommandRule(
      final UserChatService userChatService,
      final RulesEngine rulesEngine,
      final IssueService issueService) {
    super(userChatService, rulesEngine);
    this.issueService = issueService;
  }

  @Condition
  public boolean isValid(@Fact("command") final String command) {
    return NAME_LOWER_CASE.equals(command);
  }

  @Action
  public void execute(@Fact("event") final ChatMessageEvent event)
      throws MyteamServerErrorException, IOException {

    try {
      final List<Part> parts = event.getMessageParts();
      if (parts == null || parts.size() == 0) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getRawText("ru.mail.jira.plugins.myteam.files.not.found"));
        return;
      }

      final List<String> issueKeysFromString =
          JiraKeyUtils.getIssueKeysFromString(event.getMessage());
      if (CollectionUtils.isEmpty(issueKeysFromString)) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getRawText("ru.mail.jira.plugins.myteam.issue.key.not.found"));
        return;
      }

      final ApplicationUser initiator =
          userChatService.getJiraUserFromUserChatId(event.getUserId());
      if (initiator == null) {
        userChatService.sendMessageText(
            event.getChatId(),
            String.format("User not found by id %s", Utils.shieldText(event.getUserId())));
        return;
      }

      final String firstIssueKey = issueKeysFromString.get(0);
      final Issue issue = issueService.getIssue(firstIssueKey);

      final List<File> files = new ArrayList<>();
      for (final Part messagePart : parts) {
        if (messagePart instanceof Reply) {
          addFiles(((Reply) messagePart).getMessage().getParts(), files);
        } else if (messagePart instanceof Forward) {
          addFiles(((Forward) messagePart).getMessage().getParts(), files);
        }
      }

      if (files.isEmpty()) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getRawText("ru.mail.jira.plugins.myteam.files.not.found"));
        return;
      }

      final List<String> attachedFilenames = new ArrayList<>();
      final List<String> notAttachedFilenames = new ArrayList<>();
      for (final File file : files) {
        final IssueTextConverter.AttachUploadInfo attachUploadInfo =
            issueService.attachFileToIssue(issue, file, initiator);
        if (attachUploadInfo == null) {
          continue;
        }
        if (attachUploadInfo.isAttached()) {
          attachedFilenames.add(attachUploadInfo.getFileName());
        } else {
          notAttachedFilenames.add(attachUploadInfo.getFileName());
        }
      }

      final String markdownIssueLink = messageFormatter.createMarkdownIssueLink(issue.getKey());
      if (attachedFilenames.size() != 0) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getText(
                "ru.mail.jira.plugins.myteam.files.attached",
                attachedFilenames.stream().map(Utils::shieldText).collect(Collectors.joining(", ")),
                markdownIssueLink));
      }

      if (notAttachedFilenames.size() != 0) {
        userChatService.sendMessageText(
            event.getChatId(),
            userChatService.getText(
                "ru.mail.jira.plugins.myteam.files.not.attached",
                notAttachedFilenames.stream()
                    .map(Utils::shieldText)
                    .collect(Collectors.joining(", ")),
                markdownIssueLink));
      }
    } catch (final IssueNotFoundException infe) {
      rulesEngine.fireError(ErrorRuleType.IssueNotFound, event, infe);
    }
  }

  private static void addFiles(final List<Part> parts, final List<File> files) {
    if (parts != null) {
      for (final Part part : parts) {
        if (part instanceof File) {
          files.add((File) part);
        }
      }
    }
  }
}
