package ru.mail.jira.plugins.mrimsender.protocol;

import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.LocaleManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.MentionIssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.Field;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.security.IssueSecurityLevel;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.project.ProjectConstant;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.sal.api.message.I18nResolver;
import org.apache.commons.lang3.StringUtils;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import ru.mail.jira.plugins.mrimsender.icq.dto.InlineKeyboardMarkupButton;

import java.util.*;

public class MessageFormatter {
    private final ApplicationProperties applicationProperties;
    private final ConstantsManager constantsManager;
    private final DateTimeFormatter dateTimeFormatter;
    private final FieldManager fieldManager;
    private final IssueSecurityLevelManager issueSecurityLevelManager;
    private final I18nHelper i18nHelper;
    private final IssueTypeScreenSchemeManager issueTypeScreenSchemeManager;
    private final FieldScreenManager fieldScreenManager;
    private final I18nResolver i18nResolver;
    private final LocaleManager localeManager;


    public MessageFormatter(ApplicationProperties applicationProperties,
                            ConstantsManager constantsManager,
                            DateTimeFormatter dateTimeFormatter,
                            FieldManager fieldManager,
                            IssueSecurityLevelManager issueSecurityLevelManager,
                            I18nHelper i18nHelper,
                            IssueTypeScreenSchemeManager issueTypeScreenSchemeManager,
                            FieldScreenManager fieldScreenManager,
                            I18nResolver i18nResolver,
                            LocaleManager localeManager) {
        this.applicationProperties = applicationProperties;
        this.constantsManager = constantsManager;
        this.dateTimeFormatter = dateTimeFormatter;
        this.fieldManager = fieldManager;
        this.issueSecurityLevelManager = issueSecurityLevelManager;
        this.i18nHelper = i18nHelper;
        this.issueTypeScreenSchemeManager = issueTypeScreenSchemeManager;
        this.fieldScreenManager = fieldScreenManager;
        this.i18nResolver = i18nResolver;
        this.localeManager = localeManager;
    }

    private String formatUser(ApplicationUser user, String messageKey, boolean mention) {
        if (user != null) {
            if (mention) {
                return "@[" + user.getEmailAddress() + "]";
            }
            return user.getDisplayName() + " (" + user.getEmailAddress() + ")";
        } else
            return i18nHelper.getText(messageKey);
    }

    private String formatPriority(Priority priority) {
        if (priority != null && !priority.getId().equals(constantsManager.getDefaultPriorityObject().getId()))
            return priority.getNameTranslation(i18nHelper);
        else
            return null;
    }

    private void appendField(StringBuilder sb, String title, String value, boolean appendEmpty) {
        if (appendEmpty || !StringUtils.isBlank(value)) {
            if (sb.length() == 0)
                sb.append("\n");
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
                    value.append(((ProjectConstant) object).getName());
                if (object instanceof Attachment)
                    value.append(((Attachment) object).getFilename());
                if (object instanceof Label)
                    value.append(((Label) object).getLabel());
                if (iterator.hasNext())
                    value.append(", ");
            }
            appendField(sb, title, value.toString(), false);
        }
    }

    public String formatSystemFields(ApplicationUser recipient, Issue issue, boolean useMentionFormat) {
        StringBuilder sb = new StringBuilder();

        if (issue.getIssueType() != null)
            appendField(sb, i18nHelper.getText("issue.field.issuetype"), issue.getIssueType().getNameTranslation(i18nHelper), false);

        appendField(sb, i18nHelper.getText("issue.field.affectsversions"), issue.getAffectedVersions());
        appendField(sb, i18nHelper.getText("issue.field.assignee"), formatUser(issue.getAssignee(), "common.concepts.unassigned", useMentionFormat), false);
        appendField(sb, i18nHelper.getText("issue.field.attachment"), issue.getAttachments());
        appendField(sb, i18nHelper.getText("issue.field.components"), issue.getComponents());

        if (issue.getCreated() != null)
            appendField(sb, i18nHelper.getText("issue.field.created"), dateTimeFormatter.forUser(recipient).withStyle(DateTimeStyle.COMPLETE).format(issue.getCreated()), false);

        if (issue.getDueDate() != null)
            appendField(sb, i18nHelper.getText("issue.field.duedate"), dateTimeFormatter.forUser(recipient).withSystemZone().withStyle(DateTimeStyle.DATE).format(issue.getDueDate()), false);

        appendField(sb, i18nHelper.getText("issue.field.environment"), issue.getEnvironment(), false);
        appendField(sb, i18nHelper.getText("issue.field.fixversions"), issue.getFixVersions());
        appendField(sb, i18nHelper.getText("issue.field.labels"), issue.getLabels());
        appendField(sb, i18nHelper.getText("issue.field.priority"), formatPriority(issue.getPriority()), false);
        appendField(sb, i18nHelper.getText("issue.field.reporter"), formatUser(issue.getReporter(), "common.concepts.no.reporter", useMentionFormat), false);

        if (issue.getSecurityLevelId() != null) {
            IssueSecurityLevel issueSecurityLevel = issueSecurityLevelManager.getSecurityLevel(issue.getSecurityLevelId());
            String value = issueSecurityLevel.getName();
            if (!StringUtils.isBlank(issueSecurityLevel.getDescription()))
                value += " " + issueSecurityLevel.getDescription();
            appendField(sb, i18nHelper.getText("issue.field.securitylevel"), value, false);
        }

        if (!StringUtils.isBlank(issue.getDescription()))
            sb.append("\n\n").append(issue.getDescription());

        return sb.toString();
    }

    private String formatChangeLog(GenericValue changeLog, boolean ignoreAssigneeField) {
        StringBuilder sb = new StringBuilder();
        if (changeLog != null)
            try {
                String changedDescription = null;

                for (GenericValue changeItem : changeLog.getRelated("ChildChangeItem")) {
                    String field = StringUtils.defaultString(changeItem.getString("field"));
                    String newString = StringUtils.defaultString(changeItem.getString("newstring"));

                    if ("description".equals(field)) {
                        changedDescription = newString;
                        continue;
                    }
                    if ("WorklogTimeSpent".equals(field) || "WorklogId".equals(field) || "assignee".equals(field) && ignoreAssigneeField)
                        continue;

                    String title = field;
                    if (!"custom".equalsIgnoreCase(changeItem.getString("fieldtype")))
                        title = i18nHelper.getText("issue.field." + field.replaceAll(" ", "").toLowerCase());

                    if (("Fix Version".equals(field) || "Component".equals(field) || "Version".equals(field))
                            && changeItem.get("oldvalue") != null && changeItem.get("newvalue") == null) {
                        newString = changeItem.getString("oldstring");
                        title = i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.deleted", title);
                    }

                    if (fieldManager.isNavigableField(field)) {
                        final NavigableField navigableField = fieldManager.getNavigableField(field);
                        if (navigableField != null)
                            newString = navigableField.prettyPrintChangeHistory(newString, i18nHelper);
                    }

                    appendField(sb, title, newString, true);
                }

                if (!StringUtils.isBlank(changedDescription))
                    sb.append("\n\n").append(changedDescription);
            } catch (GenericEntityException ignored) {
            }
        return sb.toString();
    }

    public String formatEvent(ApplicationUser recipient, IssueEvent issueEvent) {
        Issue issue = issueEvent.getIssue();
        ApplicationUser user = issueEvent.getUser();
        String issueLink = String.format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issue.getKey());

        StringBuilder sb = new StringBuilder();

        boolean useMentionFormat = !recipient.equals(user);
        Long eventTypeId = issueEvent.getEventTypeId();
        if (EventType.ISSUE_CREATED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.created", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_UPDATED_ID.equals(eventTypeId) || EventType.ISSUE_COMMENT_DELETED_ID.equals(eventTypeId) || EventType.ISSUE_GENERICEVENT_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.updated", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_ASSIGNED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.assigned", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink, formatUser(issue.getAssignee(), "common.concepts.unassigned", useMentionFormat)));
        } else if (EventType.ISSUE_RESOLVED_ID.equals(eventTypeId)) {
            Resolution resolution = issue.getResolution();
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.resolved", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink, resolution != null ? resolution.getNameTranslation(i18nHelper) : i18nHelper.getText("common.resolution.unresolved")));
        } else if (EventType.ISSUE_CLOSED_ID.equals(eventTypeId)) {
            Resolution resolution = issue.getResolution();
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.closed", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink, resolution != null ? resolution.getNameTranslation(i18nHelper) : i18nHelper.getText("common.resolution.unresolved")));
        } else if (EventType.ISSUE_COMMENTED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.commented", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_COMMENT_EDITED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.commentEdited", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_REOPENED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.reopened", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_DELETED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.deleted", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_MOVED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.moved", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_WORKLOGGED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.worklogged", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_WORKSTARTED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.workStarted", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_WORKSTOPPED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.workStopped", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_WORKLOG_UPDATED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.worklogUpdated", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else if (EventType.ISSUE_WORKLOG_DELETED_ID.equals(eventTypeId)) {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.worklogDeleted", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        } else {
            sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.updated", formatUser(user, "common.words.anonymous", useMentionFormat), issueLink));
        }

        sb.append("\n").append(issue.getSummary());

        if (issueEvent.getWorklog() != null && !StringUtils.isBlank(issueEvent.getWorklog().getComment()))
            sb.append("\n\n").append(issueEvent.getWorklog().getComment());

        if (EventType.ISSUE_CREATED_ID.equals(eventTypeId))
            sb.append(formatSystemFields(recipient, issue, useMentionFormat));

        sb.append(formatChangeLog(issueEvent.getChangeLog(), EventType.ISSUE_ASSIGNED_ID.equals(eventTypeId)));

        if (issueEvent.getComment() != null && !StringUtils.isBlank(issueEvent.getComment().getBody()))
            sb.append("\n\n").append(issueEvent.getComment().getBody());

        return sb.toString();
    }

    public String formatEvent(MentionIssueEvent mentionIssueEvent) {
        Issue issue = mentionIssueEvent.getIssue();
        ApplicationUser user = mentionIssueEvent.getFromUser();
        String issueLink = String.format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issue.getKey());

        StringBuilder sb = new StringBuilder();
        sb.append(i18nHelper.getText("ru.mail.jira.plugins.mrimsender.notification.mentioned", formatUser(user, "common.words.anonymous", true), issueLink));
        sb.append("\n").append(issue.getSummary());

        if (!StringUtils.isBlank(mentionIssueEvent.getMentionText()))
            sb.append("\n\n").append(mentionIssueEvent.getMentionText());

        return sb.toString();
    }

    public String createIssueSummary(Issue issue, ApplicationUser user) {
        StringBuilder sb = new StringBuilder();
        sb.append(issue.getKey()).append("   ").append(issue.getSummary()).append("\n");
        String issueLink = String.format("%s/browse/%s", applicationProperties.getString(APKeys.JIRA_BASEURL), issue.getKey());
        sb.append(issueLink);
        sb.append(formatSystemFields(user, issue, false));
        FieldScreenScheme fieldScreenScheme = issueTypeScreenSchemeManager.getFieldScreenScheme(issue);
        FieldScreen fieldScreen = fieldScreenScheme.getFieldScreen(IssueOperations.VIEW_ISSUE_OPERATION);

        fieldScreenManager
                .getFieldScreenTabs(fieldScreen)
                .forEach(tab -> fieldScreenManager
                        .getFieldScreenLayoutItems(tab)
                        .forEach(fieldScreenLayoutItem -> {
                            Field field = fieldManager.getField(fieldScreenLayoutItem.getFieldId());
                            if (fieldManager.isCustomField(field)) {
                                CustomField customField = (CustomField) field;
                                appendField(sb, customField.getFieldName(), customField.getValueFromIssue(issue), false);
                            }
                        })
                );

        return sb.toString();
    }

    public List<List<InlineKeyboardMarkupButton>> getAllIssueButtons(String issueKey, ApplicationUser recipient) {
        List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();
        List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
        buttons.add(buttonsRow);

        InlineKeyboardMarkupButton issueInfo = new InlineKeyboardMarkupButton();
        issueInfo.setText(i18nResolver.getRawText(localeManager.getLocaleFor(recipient), "ru.mail.jira.plugins.mrimsender.mrimsenderEventListener.quickViewButton.text"));
        issueInfo.setCallbackData(String.join("-", "view", issueKey));
        buttonsRow.add(issueInfo);

        InlineKeyboardMarkupButton comment = new InlineKeyboardMarkupButton();
        comment.setText(i18nResolver.getRawText(localeManager.getLocaleFor(recipient), "ru.mail.jira.plugins.mrimsender.mrimsenderEventListener.commentButton.text"));
        comment.setCallbackData(String.join("-", "comment", issueKey));
        buttonsRow.add(comment);

        return buttons;
    }

    public List<List<InlineKeyboardMarkupButton>> getIssueButtons(String issueKey, ApplicationUser recipient) {
        List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();
        List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
        buttons.add(buttonsRow);

        InlineKeyboardMarkupButton comment = new InlineKeyboardMarkupButton();
        comment.setText(i18nResolver.getRawText(localeManager.getLocaleFor(recipient), "ru.mail.jira.plugins.mrimsender.mrimsenderEventListener.commentButton.text"));
        comment.setCallbackData(String.join("-", "comment", issueKey));
        buttonsRow.add(comment);

        return buttons;
    }

    public List<List<InlineKeyboardMarkupButton>> getCancelButton(ApplicationUser recipient) {
        List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();
        List<InlineKeyboardMarkupButton> buttonsRow = new ArrayList<>();
        buttons.add(buttonsRow);

        InlineKeyboardMarkupButton cancel = new InlineKeyboardMarkupButton();
        cancel.setText(i18nResolver.getRawText(localeManager.getLocaleFor(recipient), "ru.mail.jira.plugins.mrimsender.mrimsenderEventListener.cancelButton.text"));
        cancel.setCallbackData("cancel");
        buttonsRow.add(cancel);

        return buttons;
    }

    private void addRowWithButton(List<List<InlineKeyboardMarkupButton>> buttons, InlineKeyboardMarkupButton button) {
        List<InlineKeyboardMarkupButton> newButtonsRow = new ArrayList<>(1);
        newButtonsRow.add(button);
        buttons.add(newButtonsRow);
    }

    public List<List<InlineKeyboardMarkupButton>> getMenuButtons(Locale locale) {
        List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>();

        // create 'search issue' button
        InlineKeyboardMarkupButton showIssueButton = InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.showIssueButton.text"), "showIssue");
        addRowWithButton(buttons, showIssueButton);

        // create 'Active issues assigned to me' button
        InlineKeyboardMarkupButton activeAssignedIssuesButton = InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale,  "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.activeIssuesAssignedToMeButton.text"), "activeIssuesAssigned");
        addRowWithButton(buttons, activeAssignedIssuesButton);

        // create 'Active issues i watching' button
        InlineKeyboardMarkupButton activeWatchingIssuesButton = InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.activeIssuesWatchingByMeButton.text"), "activeIssuesWatching");
        addRowWithButton(buttons, activeWatchingIssuesButton);

        // create 'Active issues crated by me' button
        InlineKeyboardMarkupButton activeCreatedIssuesButton = InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.activeIssuesCreatedByMeButton.text"), "activeIssuesCreated");
        addRowWithButton(buttons, activeCreatedIssuesButton);

        //create 'Search issue by JQL' button
        InlineKeyboardMarkupButton searchIssueByJqlButton = InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.searchIssueByJqlButton.text"), "searchByJql");
        addRowWithButton(buttons, searchIssueByJqlButton);

        // create 'create issue' button
        /*InlineKeyboardMarkupButton createIssueButton = new InlineKeyboardMarkupButton();
        createIssueButton.setText(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.mainMenu.createIssueButton.text"));
        createIssueButton.setCallbackData("create");
        buttonsRow2.add(createIssueButton);*/
        return buttons;
    }

    public List<List<InlineKeyboardMarkupButton>> getListButtons(Locale locale) {
        return getListButtons(locale, true, true);
    }

    public List<List<InlineKeyboardMarkupButton>> getListButtons(Locale locale, boolean withPrev, boolean withNext) {
        if (!withPrev && !withNext)
            return null;
        List<List<InlineKeyboardMarkupButton>> buttons = new ArrayList<>(1);
        List<InlineKeyboardMarkupButton> newButtonsRow = new ArrayList<>();
        if (withPrev) {
            newButtonsRow.add(InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.listButtons.prevPageButton.text"), "prevListPage"));
        }
        if (withNext) {
            newButtonsRow.add(InlineKeyboardMarkupButton.buildButtonWithoutUrl(i18nResolver.getRawText(locale, "ru.mail.jira.plugins.mrimsender.messageFormatter.listButtons.nextPageButton.text"), "nextListPage"));
        }
        buttons.add(newButtonsRow);
        return buttons;
    }

    public String stringifyIssueList(List<Issue> issueList, int pageNumber, int pageSize) {
        StringBuilder sb = new StringBuilder();
        // example for pageSize = 15 : 1, 16, 31...
        int strIndex = pageNumber * pageSize + 1;
        for (Issue issue: issueList) {
            sb.append(strIndex);
            sb.append(". ");
            sb.append(issue.getKey());
            sb.append(" ");
            sb.append(issue.getSummary());
            sb.append("\n");
            strIndex++;
        }
        return sb.toString();
    }
}
