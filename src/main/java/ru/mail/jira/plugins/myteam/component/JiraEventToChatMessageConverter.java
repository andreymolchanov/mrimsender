/* (C)2023 */
package ru.mail.jira.plugins.myteam.component;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static ru.mail.jira.plugins.myteam.commons.Utils.shieldText;

import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.MentionIssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.fields.UserField;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.commons.SentryClient;

@Component
@Slf4j
public class JiraEventToChatMessageConverter {
  private static final List<Pattern> PATTERNS_TO_EXCLUDE_DESCRIPTION_FOR_DIFF =
      initPatternsToExcludeDescriptionForDiff();
  private static final Map<Long, String> EVENT_TYPE_MAP = getEventTypeMap();
  private final MessageFormatter messageFormatter;
  private final JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter;
  private final DiffFieldChatMessageGenerator diffFieldChatMessageGenerator;
  private final AttachmentManager attachmentManager;

  private final ApplicationProperties applicationProperties;
  private final I18nResolver i18nResolver;
  private final I18nHelper i18nHelper;
  private final FieldManager fieldManager;
  private final UserManager userManager;

  public JiraEventToChatMessageConverter(
      final MessageFormatter messageFormatter,
      final JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter,
      final DiffFieldChatMessageGenerator diffFieldChatMessageGenerator,
      @ComponentImport final AttachmentManager attachmentManager,
      @ComponentImport final ApplicationProperties applicationProperties,
      @ComponentImport final I18nResolver i18nResolver,
      @ComponentImport final I18nHelper i18nHelper,
      @ComponentImport final FieldManager fieldManager,
      @ComponentImport final UserManager userManager) {
    this.messageFormatter = messageFormatter;
    this.jiraMarkdownToChatMarkdownConverter = jiraMarkdownToChatMarkdownConverter;
    this.diffFieldChatMessageGenerator = diffFieldChatMessageGenerator;
    this.attachmentManager = attachmentManager;
    this.applicationProperties = applicationProperties;
    this.i18nResolver = i18nResolver;
    this.i18nHelper = i18nHelper;
    this.fieldManager = fieldManager;
    this.userManager = userManager;
  }

  public String formatMentionEvent(final MentionIssueEvent mentionIssueEvent) {
    final Issue issue = mentionIssueEvent.getIssue();
    final ApplicationUser user = mentionIssueEvent.getFromUser();
    final String issueLink = messageFormatter.getIssueLink(issue.getKey());

    final StringBuilder sb = new StringBuilder();
    sb.append(
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.notification.mentioned",
            messageFormatter.formatUser(user, "common.words.anonymous", true),
            issueLink));
    sb.append("\n").append(shieldText(issue.getSummary()));

    if (!isBlank(mentionIssueEvent.getMentionText()))
      sb.append("\n\n")
          .append(
              jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                  mentionIssueEvent.getMentionText(), true));

    return sb.toString();
  }

  @Nullable
  public String formatEventWithDiff(final ApplicationUser recipient, final IssueEvent issueEvent) {
    final Issue issue = issueEvent.getIssue();
    final ApplicationUser user = issueEvent.getUser();
    final String issueLink =
        messageFormatter.markdownTextLink(
            issue.getKey(), messageFormatter.createIssueLink(issue.getKey()));
    final StringBuilder sb = new StringBuilder();

    final boolean useMentionFormat = !recipient.equals(user);

    final Long eventTypeId = issueEvent.getEventTypeId();

    final String eventTypeKey = EVENT_TYPE_MAP.getOrDefault(eventTypeId, "updated");
    final String i18nKey = "ru.mail.jira.plugins.myteam.notification." + eventTypeKey;

    if (EventType.ISSUE_ASSIGNED_ID.equals(eventTypeId)) {
      sb.append(
          i18nResolver.getText(
              i18nKey,
              messageFormatter.formatUser(user, "common.words.anonymous", useMentionFormat),
              issueLink,
              messageFormatter.formatUser(
                  issue.getAssignee(), "common.concepts.unassigned", useMentionFormat)));
    } else if (EventType.ISSUE_RESOLVED_ID.equals(eventTypeId)
        || EventType.ISSUE_CLOSED_ID.equals(eventTypeId)) {
      Resolution resolution = issue.getResolution();
      sb.append(
          i18nResolver.getText(
              i18nKey,
              messageFormatter.formatUser(user, "common.words.anonymous", useMentionFormat),
              issueLink,
              resolution != null
                  ? resolution.getNameTranslation(i18nHelper)
                  : i18nResolver.getText("common.resolution.unresolved")));
    } else if (EventType.ISSUE_COMMENTED_ID.equals(eventTypeId)
        || EventType.ISSUE_COMMENT_EDITED_ID.equals(eventTypeId)) {
      sb.append(
          i18nResolver.getText(
              i18nKey,
              messageFormatter.getIssueLink(issue.getKey()),
              issue.getSummary(),
              messageFormatter.formatUser(user, "common.words.anonymous", useMentionFormat),
              format(
                  "%s/browse/%s?focusedCommentId=%s&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-%s",
                  applicationProperties.getString(APKeys.JIRA_BASEURL),
                  issue.getKey(),
                  issueEvent.getComment().getId(),
                  issueEvent.getComment().getId()),
              jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                  issueEvent.getComment().getBody(), useMentionFormat)));
      return sb.toString();
    } else {
      sb.append(
          i18nResolver.getText(
              i18nKey,
              messageFormatter.formatUser(user, "common.words.anonymous", useMentionFormat),
              issueLink));
    }
    sb.append("\n").append(shieldText(issue.getSummary()));

    if (issueEvent.getWorklog() != null && !isBlank(issueEvent.getWorklog().getComment()))
      sb.append("\n\n").append(shieldText(issueEvent.getWorklog().getComment()));

    if (EventType.ISSUE_CREATED_ID.equals(eventTypeId))
      sb.append(messageFormatter.formatSystemFields(recipient, issue, useMentionFormat));

    sb.append(
        formatChangeLogWithDiff(
            issueEvent.getChangeLog(),
            EventType.ISSUE_ASSIGNED_ID.equals(eventTypeId),
            useMentionFormat));
    if (issueEvent.getComment() != null && !isBlank(issueEvent.getComment().getBody())) {
      if (EventType.ISSUE_COMMENTED_ID.equals(eventTypeId)
          && issueEvent.getComment().getBody().contains("[~" + recipient.getName() + "]")) {
        // do not send message when recipient mentioned in comment
        return null;
      }

      sb.append("\n\n")
          .append(
              jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                  issueEvent.getComment().getBody(), useMentionFormat));
    }
    return sb.toString();
  }

  private String formatChangeLogWithDiff(
      final GenericValue changeLog,
      final boolean ignoreAssigneeField,
      final boolean useMentionFormat) {
    final StringBuilder sb = new StringBuilder();
    if (changeLog != null)
      try {
        String changedDescription = null;
        String oldDesc = null;
        boolean oldOrNewDescHasComplexFormatting = false;
        for (GenericValue changeItem : changeLog.getRelated("ChildChangeItem")) {
          String field = StringUtils.defaultString(changeItem.getString("field"));
          String newString = StringUtils.defaultString(changeItem.getString("newstring"));

          if ("description".equals(field)) {
            oldDesc = StringUtils.defaultString(changeItem.getString("oldstring"));
            oldOrNewDescHasComplexFormatting = checkDescOnComplexJiraWikiStyles(oldDesc, newString);
            oldDesc = messageFormatter.limitFieldValue(oldDesc);
            changedDescription = messageFormatter.limitFieldValue(newString);
            continue;
          }
          if ("WorklogTimeSpent".equals(field)
              || "WorklogId".equals(field)
              || ("assignee".equals(field) && ignoreAssigneeField)) continue;

          String title = field;
          if ("Attachment".equals(field)) {
            String attachmentId = changeItem.getString("newvalue");
            if (StringUtils.isNotEmpty(attachmentId)) {
              Attachment attachment = attachmentManager.getAttachment(Long.valueOf(attachmentId));
              String jiraBaseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
              try {
                sb.append("\n\n")
                    .append(
                        messageFormatter.markdownTextLink(
                            attachment.getFilename(),
                            new URI(
                                    format(
                                        "%s/secure/attachment/%d/%s",
                                        jiraBaseUrl, attachment.getId(), attachment.getFilename()),
                                    false,
                                    StandardCharsets.UTF_8.toString())
                                .getEscapedURI()));
              } catch (URIException e) {
                SentryClient.capture(e);
                log.error(
                    "Can't find attachment id:{} name:{}",
                    changeItem.getString("newvalue"),
                    changeItem.getString("newstring"),
                    e);
              }
            } else {
              sb.append("\n\n")
                  .append(
                      i18nResolver.getText(
                          "ru.mail.jira.plugins.myteam.notification.attachmentDeleted",
                          changeItem.getString("oldstring")));
            }
            continue;
          }
          if (!"custom".equalsIgnoreCase(changeItem.getString("fieldtype")))
            title = i18nResolver.getText("issue.field." + field.replaceAll(" ", "").toLowerCase());

          String oldString = StringUtils.defaultString(changeItem.getString("oldstring"));
          if (("Fix Version".equals(field) || "Component".equals(field) || "Version".equals(field))
              && changeItem.get("oldvalue") != null
              && changeItem.get("newvalue") == null) {
            title = i18nResolver.getText("ru.mail.jira.plugins.myteam.notification.deleted", title);
            appendFieldOldAndNewValue(sb, title, shieldText(oldString), "", true);
            continue;
          }

          if (fieldManager.isNavigableField(field)) {
            final NavigableField navigableField = fieldManager.getNavigableField(field);
            if (navigableField != null) {
              if (navigableField instanceof UserField) {
                appendFieldOldAndNewValue(
                    sb,
                    title,
                    messageFormatter.formatUser(
                        userManager.getUserByKey(changeItem.getString("oldvalue")),
                        "common.words.anonymous",
                        true),
                    messageFormatter.formatUser(
                        userManager.getUserByKey(changeItem.getString("newvalue")),
                        "common.words.anonymous",
                        true),
                    true);
                continue;
              }
              newString = navigableField.prettyPrintChangeHistory(newString);
              oldString = navigableField.prettyPrintChangeHistory(oldString);
            }
          }

          appendFieldOldAndNewValue(sb, title, shieldText(oldString), shieldText(newString), true);
        }

        if (!isBlank(changedDescription))
          if (oldOrNewDescHasComplexFormatting) {
            sb.append("\n\n")
                .append(
                    jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                        changedDescription, useMentionFormat));
          } else {
            sb.append("\n\n");
            if (isBlank(oldDesc)) {
              sb.append(
                  StringUtils.defaultString(
                      jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                          changedDescription, useMentionFormat),
                      ""));
            } else {
              String diff =
                  diffFieldChatMessageGenerator.buildDiffString(
                      StringUtils.defaultString(
                          jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                              oldDesc, useMentionFormat),
                          ""),
                      StringUtils.defaultString(
                          jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                              changedDescription, useMentionFormat),
                          ""));
              sb.append(diff)
                  .append("\n\n")
                  .append(">___")
                  .append(
                      i18nResolver.getText(
                          "ru.mail.jira.plugins.myteam.notify.diff.description.field"))
                  .append("___");
            }
          }
      } catch (Exception e) {
        SentryClient.capture(e);
      }
    return sb.toString();
  }

  private static boolean checkDescOnComplexJiraWikiStyles(
      final String oldDesc, final String newDesc) {
    boolean oldOrNewDescHasComplexFormatting = false;
    for (final Pattern pattern : PATTERNS_TO_EXCLUDE_DESCRIPTION_FOR_DIFF) {
      if (checkDescOnComplexJiraWikiStyles(oldDesc, pattern)
          || checkDescOnComplexJiraWikiStyles(newDesc, pattern)) {
        oldOrNewDescHasComplexFormatting = true;
        break;
      }
    }

    return oldOrNewDescHasComplexFormatting;
  }

  private static boolean checkDescOnComplexJiraWikiStyles(
      final String inputText, final Pattern pattern) {
    return pattern.matcher(inputText).find();
  }

  private void appendFieldOldAndNewValue(
      final StringBuilder sb,
      @Nullable final String title,
      @Nullable String oldValue,
      @Nullable String value,
      boolean appendEmpty) {
    if (appendEmpty || !isBlank(value) || !isBlank(oldValue)) {
      if (sb.length() == 0) {
        sb.append("\n");
      }
      if (oldValue == null) {
        oldValue = "";
      }
      if (value == null) {
        value = "";
      }
      sb.append("\n").append(title).append(": ");
      if (oldValue.isEmpty()) {
        sb.append(diffFieldChatMessageGenerator.markNewValue(value));
      } else if (value.isEmpty()) {
        sb.append(diffFieldChatMessageGenerator.markOldValue(oldValue));
      } else {
        sb.append(oldValue.isEmpty() ? "" : diffFieldChatMessageGenerator.markOldValue(oldValue))
            .append(" ")
            .append(value.isEmpty() ? "" : diffFieldChatMessageGenerator.markNewValue(value));
      }
    }
  }

  @NotNull
  private static Map<Long, String> getEventTypeMap() {
    final Map<Long, String> eventTypeMap = new HashMap<>();
    eventTypeMap.put(EventType.ISSUE_CREATED_ID, "created");
    eventTypeMap.put(EventType.ISSUE_ASSIGNED_ID, "assigned");
    eventTypeMap.put(EventType.ISSUE_RESOLVED_ID, "resolved");
    eventTypeMap.put(EventType.ISSUE_CLOSED_ID, "closed");
    eventTypeMap.put(EventType.ISSUE_COMMENTED_ID, "commented");
    eventTypeMap.put(EventType.ISSUE_COMMENT_EDITED_ID, "commentEdited");
    eventTypeMap.put(EventType.ISSUE_REOPENED_ID, "reopened");
    eventTypeMap.put(EventType.ISSUE_DELETED_ID, "deleted");
    eventTypeMap.put(EventType.ISSUE_MOVED_ID, "moved");
    eventTypeMap.put(EventType.ISSUE_WORKLOGGED_ID, "worklogged");
    eventTypeMap.put(EventType.ISSUE_WORKSTARTED_ID, "workStarted");
    eventTypeMap.put(EventType.ISSUE_WORKSTOPPED_ID, "workStopped");
    eventTypeMap.put(EventType.ISSUE_WORKLOG_UPDATED_ID, "worklogUpdated");
    eventTypeMap.put(EventType.ISSUE_WORKLOG_DELETED_ID, "worklogDeleted");
    return Collections.unmodifiableMap(eventTypeMap);
  }

  private static List<Pattern> initPatternsToExcludeDescriptionForDiff() {
    final List<Pattern> patterns = new ArrayList<>();
    // quotes in desc
    patterns.add(JiraMarkdownTextPattern.QUOTES_PATTERN);
    // code block in desc
    patterns.add(JiraMarkdownTextPattern.CODE_BLOCK_PATTERN_1);
    patterns.add(JiraMarkdownTextPattern.CODE_BLOCK_PATTERN_2);
    // strikethrough text in desc
    patterns.add(JiraMarkdownTextPattern.STRIKETHROUGH_PATTERN);
    // bold text in desc
    patterns.add(JiraMarkdownTextPattern.BOLD_PATTERN);
    // ordered/unordered lists
    patterns.add(JiraMarkdownTextPattern.MULTILEVEL_NUMBERED_LIST_PATTERN);

    return Collections.unmodifiableList(patterns);
  }
}
