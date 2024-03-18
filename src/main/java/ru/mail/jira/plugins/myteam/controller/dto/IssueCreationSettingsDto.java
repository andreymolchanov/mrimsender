/* (C)2022 */
package ru.mail.jira.plugins.myteam.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.*;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.commons.IssueReporter;
import ru.mail.jira.plugins.myteam.db.model.IssueCreationSettings;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressWarnings({"MissingSummary", "NullAway", "CanIgnoreReturnValueSuggester"})
public class IssueCreationSettingsDto {

  public static String LABELS_DELIMITER = ";";
  @XmlElement private Integer id;
  @XmlElement private String chatId;
  @XmlElement private Boolean enabled;
  @XmlElement private String projectKey;
  @XmlElement private Long projectId;
  @XmlElement private String issueTypeId;
  @XmlElement private String tag;
  @XmlElement private String creationSuccessTemplate;
  @XmlElement private String issueSummaryTemplate;
  @XmlElement private String issueQuoteMessageTemplate;
  @Nullable @XmlElement private String chatLink;
  @Nullable @XmlElement private String chatTitle;
  @Nullable @XmlElement private String issueTypeName;
  @XmlElement private Boolean canEdit;
  @XmlElement private Boolean creationByAllMembers;
  @XmlElement private IssueReporter reporter;
  @XmlElement private String assignee;
  @XmlElement private Boolean addReporterInWatchers;
  @XmlElement private List<String> labels;
  @XmlElement private List<AdditionalIssueFieldDto> additionalFields;
  @XmlElement private Boolean allowedCreateChatLink;
  @XmlElement private Boolean allowedDeleteReplyMessage;
  @XmlElement private Boolean allowedHandleCfValues;

  public IssueCreationSettingsDto(IssueCreationSettings entity) {
    this.id = entity.getID();
    this.chatId = entity.getChatId();
    this.enabled = entity.isEnabled();
    this.tag = entity.getTag();
    this.creationByAllMembers = entity.isCreationByAllMembers();
    this.reporter = entity.getReporter();
    this.assignee = entity.getAssignee();
    this.addReporterInWatchers = entity.isAddReporterInWatchers();
    this.projectId = entity.getProjectId();
    this.issueTypeId = entity.getIssueTypeId();
    this.creationSuccessTemplate = entity.getCreationSuccessTemplate();
    this.issueSummaryTemplate = entity.getIssueSummaryTemplate();
    this.issueQuoteMessageTemplate = entity.getIssueQuoteMessageTemplate();
    this.labels =
        entity.getLabels() != null
            ? Arrays.asList(entity.getLabels().split(LABELS_DELIMITER))
            : null;
    this.additionalFields =
        Arrays.stream(entity.getAdditionalFields())
            .map(AdditionalIssueFieldDto::new)
            .collect(Collectors.toList());
    this.allowedCreateChatLink = entity.isAllowedCreateChatLink();
    this.allowedDeleteReplyMessage = entity.isAllowedDeleteReplyMessage();
    this.allowedHandleCfValues = entity.isAllowedHandleCfValues();
  }

  public IssueCreationSettingsDto(IssueCreationSettings entity, @Nullable String chatLink) {
    this.canEdit = true;
    this.id = entity.getID();
    this.chatId = entity.getChatId();
    this.enabled = entity.isEnabled();
    this.tag = entity.getTag();
    this.creationByAllMembers = entity.isCreationByAllMembers();
    this.reporter = entity.getReporter();
    this.assignee = entity.getAssignee();
    this.addReporterInWatchers = entity.isAddReporterInWatchers();
    this.projectId = entity.getProjectId();
    this.issueTypeId = entity.getIssueTypeId();
    this.creationSuccessTemplate = entity.getCreationSuccessTemplate();
    this.issueSummaryTemplate = entity.getIssueSummaryTemplate();
    this.issueQuoteMessageTemplate = entity.getIssueQuoteMessageTemplate();
    this.labels =
        entity.getLabels() != null
            ? Arrays.asList(entity.getLabels().split(LABELS_DELIMITER))
            : null;
    this.additionalFields =
        Arrays.stream(entity.getAdditionalFields())
            .map(AdditionalIssueFieldDto::new)
            .collect(Collectors.toList());
    this.chatLink = chatLink;
    this.allowedCreateChatLink = entity.isAllowedCreateChatLink();
    this.allowedDeleteReplyMessage = entity.isAllowedDeleteReplyMessage();
    this.allowedHandleCfValues = entity.isAllowedHandleCfValues();
  }
}
