/* (C)2020 */
package ru.mail.jira.plugins.myteam.component;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static ru.mail.jira.plugins.myteam.commons.Utils.shieldText;

import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueFieldConstants;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.fields.*;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.security.IssueSecurityLevel;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.project.ProjectConstant;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.commons.SentryClient;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.ButtonRuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.ruletypes.CommandRuleType;
import ru.mail.jira.plugins.myteam.bot.rulesengine.rules.buttons.ReplyRule;
import ru.mail.jira.plugins.myteam.commons.Utils;
import ru.mail.jira.plugins.myteam.component.url.dto.Link;
import ru.mail.jira.plugins.myteam.component.url.dto.LinksInMessage;
import ru.mail.jira.plugins.myteam.myteam.dto.InlineKeyboardMarkupButton;
import ru.mail.jira.plugins.myteam.myteam.dto.User;
import ru.mail.jira.plugins.myteam.service.PluginData;

@Slf4j
@Component
public class MessageFormatter {
  public static final int LIST_PAGE_SIZE = 15;
  public static final int MAX_MESSAGE_COUNT = 50;
  public static final String DELIMITER_STR = "----------";
  public static final String COMMON_USER_MESSAGE_KEY = "common.words.anonymous";
  private static final int DISPLAY_FIELD_CHARS_LIMIT = 5000;

  @SuppressWarnings("InlineFormatString")
  private static final String DESCRIPTION_MARKDOWN_MASKED_LINK_TEMPLATE = "[%s|%s]";

  @SuppressWarnings("InlineFormatString")
  private static final String DESCRIPTION_MARKDOWN_UNMASKED_LINK_TEMPLATE = "%s";

  private final ApplicationProperties applicationProperties;
  private final DateTimeFormatter dateTimeFormatter;
  private final FieldManager fieldManager;
  private final IssueSecurityLevelManager issueSecurityLevelManager;
  private final I18nHelper i18nHelper;
  private final IssueTypeScreenSchemeManager issueTypeScreenSchemeManager;
  private final FieldScreenManager fieldScreenManager;
  private final I18nResolver i18nResolver;
  private final String jiraBaseUrl;
  private final PluginData pluginData;
  private final JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter;

  @Autowired
  public MessageFormatter(
      @ComponentImport ApplicationProperties applicationProperties,
      @ComponentImport DateTimeFormatter dateTimeFormatter,
      @ComponentImport FieldManager fieldManager,
      @ComponentImport IssueSecurityLevelManager issueSecurityLevelManager,
      @ComponentImport I18nHelper i18nHelper,
      @ComponentImport IssueTypeScreenSchemeManager issueTypeScreenSchemeManager,
      @ComponentImport FieldScreenManager fieldScreenManager,
      @ComponentImport I18nResolver i18nResolver,
      PluginData pluginData,
      JiraMarkdownToChatMarkdownConverter jiraMarkdownToChatMarkdownConverter) {
    this.applicationProperties = applicationProperties;
    this.dateTimeFormatter = dateTimeFormatter;
    this.fieldManager = fieldManager;
    this.issueSecurityLevelManager = issueSecurityLevelManager;
    this.i18nHelper = i18nHelper;
    this.issueTypeScreenSchemeManager = issueTypeScreenSchemeManager;
    this.fieldScreenManager = fieldScreenManager;
    this.i18nResolver = i18nResolver;
    this.jiraBaseUrl = applicationProperties.getString(APKeys.JIRA_BASEURL);
    this.pluginData = pluginData;
    this.jiraMarkdownToChatMarkdownConverter = jiraMarkdownToChatMarkdownConverter;
  }

  public static void addRowWithButton(
      List<List<InlineKeyboardMarkupButton>> buttons, InlineKeyboardMarkupButton button) {
    List<InlineKeyboardMarkupButton> newButtonsRow = new ArrayList<>(1);
    newButtonsRow.add(button);
    buttons.add(newButtonsRow);
  }

  public static List<InlineKeyboardMarkupButton> getCancelButtonRow(String title) {
    List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
    buttonsRow.add(
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(title, ButtonRuleType.Cancel.getName()));
    return buttonsRow;
  }

  public static List<InlineKeyboardMarkupButton> getBackButtonRow(String title) {
    List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
    buttonsRow.add(
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(title, ButtonRuleType.Revert.getName()));
    return buttonsRow;
  }

  public static List<List<InlineKeyboardMarkupButton>> buildButtonsWithCancel(
      @Nullable List<List<InlineKeyboardMarkupButton>> buttons, String cancelButtonText) {
    if (buttons == null) {
      List<List<InlineKeyboardMarkupButton>> newButtons = new ArrayList<>();
      newButtons.add(getCancelButtonRow(cancelButtonText));
      return newButtons;
    }
    buttons.add(getCancelButtonRow(cancelButtonText));
    return buttons;
  }

  public static List<List<InlineKeyboardMarkupButton>> buildButtonsWithBack(
      @Nullable List<List<InlineKeyboardMarkupButton>> buttons, String cancelButtonText) {
    if (buttons == null) {
      List<List<InlineKeyboardMarkupButton>> newButtons = new ArrayList<>();
      newButtons.add(getBackButtonRow(cancelButtonText));
      return newButtons;
    }
    buttons.add(getBackButtonRow(cancelButtonText));
    return buttons;
  }

  public static String getUserDisplayName(User user) {
    return format(
            "%s %s", user.getFirstName(), user.getLastName() != null ? user.getLastName() : "")
        .trim();
  }

  public String getMyteamLink(String email) {
    return pluginData.getProfileLink() + email;
  }

  public String formatSystemFields(
      ApplicationUser recipient, Issue issue, boolean useMentionFormat) {
    StringBuilder sb = new StringBuilder();
    if (issue.getIssueType() != null)
      appendField(
          sb,
          i18nResolver.getRawText("issue.field.issuetype"),
          shieldText(issue.getIssueType().getNameTranslation(i18nHelper)),
          false);

    appendField(
        sb, i18nResolver.getRawText("issue.field.affectsversions"), issue.getAffectedVersions());
    appendField(
        sb,
        i18nResolver.getRawText("issue.field.assignee"),
        formatUser(issue.getAssignee(), "common.concepts.unassigned", useMentionFormat),
        false);

    appendField(sb, i18nResolver.getRawText("issue.field.components"), issue.getComponents());

    if (issue.getCreated() != null)
      appendField(
          sb,
          i18nResolver.getRawText("issue.field.created"),
          dateTimeFormatter
              .forUser(recipient)
              .withStyle(DateTimeStyle.COMPLETE)
              .format(issue.getCreated()),
          false);

    if (issue.getDueDate() != null)
      appendField(
          sb,
          i18nResolver.getRawText("issue.field.duedate"),
          dateTimeFormatter
              .forUser(recipient)
              .withSystemZone()
              .withStyle(DateTimeStyle.DATE)
              .format(issue.getDueDate()),
          false);

    String environment = issue.getEnvironment();
    if (environment != null) {
      appendField(
          sb, i18nResolver.getRawText("issue.field.environment"), shieldText(environment), false);
    }

    appendField(sb, i18nResolver.getRawText("issue.field.fixversions"), issue.getFixVersions());
    appendField(sb, i18nResolver.getRawText("issue.field.labels"), issue.getLabels());

    Priority priority = issue.getPriority();
    if (priority != null) {
      String formatPriority = formatPriority(priority);
      if (formatPriority != null) {
        appendField(
            sb, i18nResolver.getRawText("issue.field.priority"), shieldText(formatPriority), false);
      }
    }

    appendField(
        sb,
        i18nResolver.getRawText("issue.field.reporter"),
        formatUser(issue.getReporter(), "common.concepts.no.reporter", useMentionFormat),
        false);

    if (issue.getSecurityLevelId() != null) {
      IssueSecurityLevel issueSecurityLevel =
          issueSecurityLevelManager.getSecurityLevel(issue.getSecurityLevelId());
      String value = issueSecurityLevel.getName();
      if (!isBlank(issueSecurityLevel.getDescription()))
        value += " " + issueSecurityLevel.getDescription();
      appendField(
          sb, i18nResolver.getRawText("issue.field.securitylevel"), shieldText(value), false);
    }
    appendField(sb, i18nResolver.getRawText("issue.field.attachment"), issue.getAttachments());

    String description = issue.getDescription();
    if (StringUtils.isNotBlank(description)) {
      sb.append("\n\n")
          .append(
              jiraMarkdownToChatMarkdownConverter.makeMyteamMarkdownFromJira(
                  limitFieldValue(description), useMentionFormat));
    }

    return sb.toString();
  }

  public String createIssueLink(String issueKey) {
    return format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issueKey);
  }

  public String createShieldedUnmaskedIssueLink(String issueKey) {
    return Utils.shieldText(
        format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issueKey));
  }

  public String createJqlLink(String jql) {
    return format("%s/issues/?jql=%s", applicationProperties.getString(APKeys.JIRA_BASEURL), jql);
  }

  public String createFilterLink(Long id) {
    return format("%s/issues/?filter=%s", applicationProperties.getString(APKeys.JIRA_BASEURL), id);
  }

  public String createMarkdownIssueShortLink(String issueKey) {
    return format(
        "[%s](%s/browse/%s)",
        issueKey, applicationProperties.getString(APKeys.JIRA_BASEURL), issueKey);
  }

  public String createMarkdownIssueLink(String issueKey) {
    return markdownTextLink(
        issueKey,
        String.format(
            "%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issueKey));
  }

  public String formatJiraIssueCommentToLink(final Issue issue, final Comment comment) {
    return i18nResolver.getText(
        "ru.mail.jira.plugins.myteam.comment.issue.commentCreated", getCommentLink(issue, comment));
  }

  public String createIssueSummary(Issue issue, ApplicationUser user) {
    StringBuilder sb = new StringBuilder();
    sb.append(getIssueLink(issue.getKey()))
        .append("   ")
        .append(shieldText(issue.getSummary()))
        .append("\n");

    // append status field because it doesn't exist in formatSystemFields string
    appendField(
        sb,
        i18nHelper.getText(fieldManager.getField(IssueFieldConstants.STATUS).getNameKey()),
        shieldText(issue.getStatus().getNameTranslation(i18nHelper)),
        false);

    sb.append(formatSystemFields(user, issue, true));
    FieldScreenScheme fieldScreenScheme = issueTypeScreenSchemeManager.getFieldScreenScheme(issue);
    FieldScreen fieldScreen =
        fieldScreenScheme.getFieldScreen(IssueOperations.VIEW_ISSUE_OPERATION);

    fieldScreenManager
        .getFieldScreenTabs(fieldScreen)
        .forEach(
            tab ->
                fieldScreenManager
                    .getFieldScreenLayoutItems(tab)
                    .forEach(
                        fieldScreenLayoutItem -> {
                          Field field = fieldManager.getField(fieldScreenLayoutItem.getFieldId());
                          if (fieldManager.isCustomField(field)
                              && !fieldManager.isFieldHidden(user, field)) {
                            CustomField customField = (CustomField) field;
                            if (customField.isShown(issue)) {
                              String valueFromIssue = customField.getValueFromIssue(issue);
                              if (valueFromIssue != null) {
                                appendField(
                                    sb,
                                    shieldText(customField.getFieldName()),
                                    shieldText(valueFromIssue),
                                    false);
                              }
                            }
                          }
                        }));

    return sb.toString();
  }

  public String getIssueLink(String key) {
    return markdownTextLink(
        key, format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), key));
  }

  public List<List<InlineKeyboardMarkupButton>> getCancelButton() {
    List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();

    buttons.add(getCancelButtonRow());

    return buttons;
  }

  public List<InlineKeyboardMarkupButton> getCancelButtonRow() {
    return getCancelButtonRow(
        i18nResolver.getRawText(
            "ru.mail.jira.plugins.myteam.mrimsenderEventListener.cancelButton.text"));
  }

  public List<List<InlineKeyboardMarkupButton>> getMenuButtons() {
    List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();

    // create 'search issue' button
    InlineKeyboardMarkupButton showIssueButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.showIssueButton.text"),
            ButtonRuleType.SearchIssueByKeyInput.getName());
    addRowWithButton(buttons, showIssueButton);

    // create 'Active issues assigned to me' button
    InlineKeyboardMarkupButton activeAssignedIssuesButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.activeIssuesAssignedToMeButton.text"),
            CommandRuleType.AssignedIssues.getName());
    addRowWithButton(buttons, activeAssignedIssuesButton);

    // create 'Active issues i watching' button
    InlineKeyboardMarkupButton activeWatchingIssuesButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.activeIssuesWatchingByMeButton.text"),
            CommandRuleType.WatchingIssues.getName());
    addRowWithButton(buttons, activeWatchingIssuesButton);

    // create 'Active issues crated by me' button
    InlineKeyboardMarkupButton activeCreatedIssuesButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.activeIssuesCreatedByMeButton.text"),
            CommandRuleType.CreatedIssues.getName());
    addRowWithButton(buttons, activeCreatedIssuesButton);

    // create 'Search issue by JQL' button
    InlineKeyboardMarkupButton searchIssueByJqlButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.searchIssueByJqlButton.text"),
            ButtonRuleType.SearchIssueByJqlInput.getName());
    addRowWithButton(buttons, searchIssueByJqlButton);

    // create 'create issue' button
    InlineKeyboardMarkupButton createIssueButton =
        InlineKeyboardMarkupButton.buildButtonWithoutUrl(
            i18nResolver.getRawText(
                "ru.mail.jira.plugins.myteam.messageFormatter.mainMenu.createIssueButton.text"),
            ButtonRuleType.CreateIssue.getName());
    addRowWithButton(buttons, createIssueButton);
    return buttons;
  }

  @Nullable
  public List<List<InlineKeyboardMarkupButton>> getListButtons(boolean withPrev, boolean withNext) {
    if (!withPrev && !withNext) return null;
    List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>(1);

    List<InlineKeyboardMarkupButton> pagerButtonsRow = getPagerButtonsRow(withPrev, withNext);
    if (pagerButtonsRow.size() > 0) {
      buttons.add(pagerButtonsRow);
    }
    return buttons;
  }

  public List<InlineKeyboardMarkupButton> getPagerButtonsRow(boolean withPrev, boolean withNext) {
    List<InlineKeyboardMarkupButton> newButtonsRow = new ArrayList<>();
    if (withPrev) {
      newButtonsRow.add(
          InlineKeyboardMarkupButton.buildButtonWithoutUrl(
              i18nResolver.getRawText(
                  "ru.mail.jira.plugins.myteam.messageFormatter.listButtons.prevPageButton.text"),
              ButtonRuleType.PrevPage.getName()));
    }
    if (withNext) {
      newButtonsRow.add(
          InlineKeyboardMarkupButton.buildButtonWithoutUrl(
              i18nResolver.getRawText(
                  "ru.mail.jira.plugins.myteam.messageFormatter.listButtons.nextPageButton.text"),
              ButtonRuleType.NextPage.getName()));
    }
    return newButtonsRow;
  }

  public String stringifyPagedCollection(
      Collection<?> collection, int pageNumber, int total, int pageSize, String totalLink) {
    if (collection.size() == 0) return "";

    StringJoiner sj = new StringJoiner("\n\n");

    // stringify collection
    collection.forEach(obj -> sj.add(obj.toString()));

    // append string with current (and total) page number info
    if ((pageNumber + 1) * pageSize < total) {
      int firstResultPageIndex = pageNumber * pageSize + 1;
      int lastResultPageIndex = firstResultPageIndex + collection.size() - 1;
      sj.add(DELIMITER_STR);
      sj.add(
          i18nResolver.getText(
              "pager.results.displayissues.short",
              String.join(
                  " - ",
                  Integer.toString(firstResultPageIndex),
                  Integer.toString(lastResultPageIndex)),
              StringUtils.isNotBlank(totalLink) ? totalLink : Integer.toString(total)));
    }

    return sj.toString();
  }

  public String stringifyIssueList(
      List<Issue> issueList, int pageNumber, int total, String totalLink) {
    return stringifyPagedCollection(
        issueList.stream()
            .map(
                issue ->
                    markdownTextLink(issue.getKey(), createIssueLink(issue.getKey()))
                        + ' '
                        + shieldText(issue.getSummary()))
            .collect(Collectors.toList()),
        pageNumber,
        total,
        LIST_PAGE_SIZE,
        totalLink);
  }

  public String stringifyFieldsCollection(Collection<? extends Field> fields) {
    return fields.stream()
        .map(field -> i18nResolver.getRawText(field.getNameKey()))
        .collect(Collectors.joining("\n"));
  }

  public String formatMyteamUserLink(User user) {
    StringBuilder str = new StringBuilder();

    str.append("[").append(user.getFirstName()).append(" ");

    if (user.getLastName() != null) {
      str.append(" ").append(user.getLastName());
    }
    str.append("|").append(getMyteamLink(user.getUserId())).append("]");
    return str.toString();
  }

  public String formatUser(ApplicationUser user, String messageKey, boolean mention) {
    if (user != null) {
      if (mention) {
        return "@\\[" + shieldText(user.getEmailAddress()) + "\\]";
      }

      return "["
          + shieldText(user.getDisplayName())
          + "]("
          + shieldText(pluginData.getProfileLink() + user.getEmailAddress())
          + ")";
    } else return i18nHelper.getText(messageKey);
  }

  public String formatUserToVKTeamsUserMention(ApplicationUser user) {
    return formatUser(user, COMMON_USER_MESSAGE_KEY, true);
  }

  public String formatUserToVKTeamsSysProfile(ApplicationUser user) {
    return formatUser(user, COMMON_USER_MESSAGE_KEY, false);
  }

  public String formatEmptyFilterSubscription(String filterName, Long filterId) {
    return i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.subscriptions.page.subscription.message.header",
            markdownTextLink(filterName, createFilterLink(filterId)))
        + "\n\n"
        + i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.subscriptions.page.subscription.message.body.empty");
  }

  public String formatIssueFilterSubscription(String filterName, Long filterId, int totalIssues) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.subscriptions.page.subscription.message.header",
            markdownTextLink(filterName, createFilterLink(filterId))));
    sb.append("\n\n");

    String displayIssue =
        totalIssues > MAX_MESSAGE_COUNT
            ? i18nResolver.getText(
                "pager.results.displayissues.short", MAX_MESSAGE_COUNT, totalIssues)
            : String.valueOf(totalIssues);
    sb.append(displayIssue);
    sb.append(" ");
    sb.append(
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.subscriptions.page.subscription.message.body"));
    return sb.toString();
  }

  public String formatListFilterSubscription(
      String filterName, Long filterId, String searchJql, SearchResults<Issue> issueSearchResults) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.subscriptions.page.subscription.message.header",
            markdownTextLink(filterName, createFilterLink(filterId))));
    sb.append("\n\n");

    int totalIssues = issueSearchResults.getTotal();
    sb.append(
        stringifyIssueList(
            issueSearchResults.getResults(),
            0,
            totalIssues,
            markdownTextLink(Integer.toString(totalIssues), createJqlLink(searchJql))));
    return sb.toString();
  }

  public String formatAccessReplyError(Exception e) {
    return i18nResolver.getText(
        "ru.mail.jira.plugins.myteam.accessRequest.page.message.reply.error", e.getMessage());
  }

  public String formatAccessReplyRequesterMessage(
      @Nullable ApplicationUser responder, Issue issue, ReplyRule.ReplyCommands replyCommand) {
    String command =
        replyCommand.equals(ReplyRule.ReplyCommands.COMMAND_ALLOW) ? "allow" : "forbid";
    return i18nResolver.getText(
        String.format(
            "ru.mail.jira.plugins.myteam.accessRequest.page.message.reply.requester.%s", command),
        markdownTextLink(issue.getKey(), createIssueLink(issue.getKey())),
        responder != null ? formatUser(responder, "common.words.anonymous", true) : "[unknown]");
  }

  public String formatAccessReplyMessage(
      @Nullable ApplicationUser requester, Issue issue, ReplyRule.ReplyCommands replyCommand) {
    String command =
        replyCommand.equals(ReplyRule.ReplyCommands.COMMAND_ALLOW) ? "allow" : "forbid";
    return i18nResolver.getText(
        String.format("ru.mail.jira.plugins.myteam.accessRequest.page.message.reply.%s", command),
        requester != null ? formatUser(requester, "common.words.anonymous", true) : "[unknown]",
        markdownTextLink(issue.getKey(), createIssueLink(issue.getKey())));
  }

  public String formatProcessedReplyMessage(
      @Nullable ApplicationUser responder,
      @Nullable ApplicationUser requester,
      Issue issue,
      boolean replyDtoStatus) {
    return String.join(
        "\n",
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.accessRequest.page.message.reply.processed"),
        formatProcessedNewsletterMessage(responder, requester, issue, replyDtoStatus));
  }

  public String formatProcessedNewsletterMessage(
      @Nullable ApplicationUser responder,
      @Nullable ApplicationUser requester,
      Issue issue,
      boolean replyDtoStatus) {
    String command = replyDtoStatus ? "allowed" : "forbade";
    return i18nResolver.getText(
        String.format("ru.mail.jira.plugins.myteam.accessRequest.page.message.reply.%s", command),
        responder != null ? formatUser(responder, "common.words.anonymous", true) : "[unknown]",
        requester != null ? formatUser(requester, "common.words.anonymous", true) : "[unknown]",
        markdownTextLink(issue.getKey(), createIssueLink(issue.getKey())));
  }

  public String formatAccessRequestMessage(
      @NotNull ApplicationUser user, @NotNull Issue issue, @Nullable String message) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        i18nResolver.getText(
            "ru.mail.jira.plugins.myteam.accessRequest.page.message.title",
            formatUser(user, "common.words.anonymous", true),
            markdownTextLink(issue.getKey(), createIssueLink(issue.getKey())),
            shieldText(issue.getSummary())));
    if (StringUtils.isNotBlank(message)) {
      sb.append("\n");
      sb.append(
          i18nResolver.getText(
              "ru.mail.jira.plugins.myteam.accessRequest.page.message.text",
              Utils.shieldText(message)));
    }
    return sb.toString();
  }

  @Nullable
  private String formatPriority(@Nullable Priority priority) {
    if (priority != null) return priority.getNameTranslation(i18nHelper);
    else return null;
  }

  private void appendField(
      StringBuilder sb, @Nullable String title, @Nullable String value, boolean appendEmpty) {
    if (appendEmpty || !isBlank(value)) {
      if (sb.length() == 0) sb.append("\n");
      sb.append("\n").append(title).append(": ").append(StringUtils.defaultString(value));
    }
  }

  private void appendField(StringBuilder sb, String title, Collection<?> collection) {
    if (collection != null) {
      StringBuilder value = new StringBuilder();
      Iterator<?> iterator = collection.iterator();
      while (iterator.hasNext()) {
        Object object = iterator.next();
        if (object instanceof ProjectConstant)
          value.append(shieldText(((ProjectConstant) object).getName()));
        if (object instanceof Attachment) {
          Attachment attachment = (Attachment) object;
          String attachmentUrl = "";
          try {
            attachmentUrl =
                new URI(
                        format(
                            "%s/secure/attachment/%d/%s",
                            jiraBaseUrl, attachment.getId(), attachment.getFilename()),
                        false,
                        StandardCharsets.UTF_8.toString())
                    .getEscapedURI();
          } catch (URIException e) {
            SentryClient.capture(e);
            log.error("Unable to create attachment link for file: {}", attachment.getFilename());
          }
          value.append(markdownTextLink(attachment.getFilename(), attachmentUrl));
        }
        if (object instanceof Label) value.append(shieldText(((Label) object).getLabel()));
        if (iterator.hasNext()) value.append(", ");
      }
      appendField(sb, title, value.toString(), false);
    }
  }

  public String markdownTextLink(String text, String url) {
    if (text != null) {
      try {
        return "["
            + shieldText(text)
            + "]("
            + shieldText(new URI(url, false, StandardCharsets.UTF_8.toString()).getEscapedURI())
            + ")";
      } catch (URIException e) {
        SentryClient.capture(e);
        log.error("Unable to create text link for issueKey: {}, url:{}", text, url);
      }
    }
    return url;
  }

  public String limitFieldValue(@NotNull String value) {
    if (value.length() < DISPLAY_FIELD_CHARS_LIMIT) return value;
    return value.substring(0, DISPLAY_FIELD_CHARS_LIMIT) + "...";
  }

  public String formatLinks(final String messageBody, final LinksInMessage linksInMessage) {
    if (linksInMessage.getLinks().isEmpty()) {
      return messageBody;
    }

    final List<Link> links = linksInMessage.getLinks();
    String formattedMesssageBody = messageBody;
    for (final Link link : links) {
      if (link.isMasked()) {
        try {
          formattedMesssageBody =
              formattedMesssageBody.replaceFirst(
                  Pattern.quote(link.getMask()),
                  format(
                      DESCRIPTION_MARKDOWN_MASKED_LINK_TEMPLATE, link.getMask(), link.getLink()));
        } catch (Exception e) {
          SentryClient.capture(e, null, Map.of("messageBody", messageBody));
        }
      } else {
        try {
          formattedMesssageBody =
              formattedMesssageBody.replaceFirst(
                  Pattern.quote(link.getLink()),
                  format(DESCRIPTION_MARKDOWN_UNMASKED_LINK_TEMPLATE, link.getLink()));
        } catch (Exception e) {
          SentryClient.capture(e, null, Map.of("messageBody", messageBody));
        }
      }
    }

    return formattedMesssageBody;
  }

  public String getCommentLink(Issue issue, Comment comment) {
    return format(
        "%s/browse/%s?focusedCommentId=%s&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-%s",
        jiraBaseUrl, issue.getKey(), comment.getId(), comment.getId());
  }

  public String getShieldCommentLink(Issue issue, Comment comment) {
    return shieldText(getCommentLink(issue, comment));
  }
}
