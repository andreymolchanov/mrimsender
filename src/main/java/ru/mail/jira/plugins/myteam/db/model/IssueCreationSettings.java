/* (C)2022 */
package ru.mail.jira.plugins.myteam.db.model;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.schema.Index;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.Indexes;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.commons.IssueReporter;

@Table("MYTEAM_ISSUE_CFG")
@Indexes(
    @Index(
        name = "chat_id_tag",
        methodNames = {"setChatId", "setTag"}))
public interface IssueCreationSettings extends Entity {
  @Indexed
  String getChatId();

  void setChatId(String chatId);

  boolean isEnabled();

  void setEnabled(boolean enabled);

  @Indexed
  String getTag();

  void setTag(String tag);

  @Nullable
  String getProjectKey();

  void setProjectKey(String key);

  @Nullable
  String getIssueTypeId();

  void setIssueTypeId(String id);

  @Nullable
  @StringLength(StringLength.UNLIMITED)
  String getLabels();

  void setLabels(@Nullable String labels);

  boolean isCreationByAllMembers();

  void setCreationByAllMembers(boolean creationByAllMembers);

  @Nullable
  IssueReporter getReporter();

  void setReporter(IssueReporter issueReporter);

  @Nullable
  String getAssignee();

  void setAssignee(String assignee);

  boolean isAddReporterInWatchers();

  void setAddReporterInWatchers(boolean addReporterInWatchers);

  @Nullable
  @StringLength(StringLength.UNLIMITED)
  String getCreationSuccessTemplate();

  void setCreationSuccessTemplate(String template);

  @Nullable
  @StringLength(StringLength.UNLIMITED)
  String getIssueSummaryTemplate();

  void setIssueSummaryTemplate(String template);

  @Nullable
  @StringLength(StringLength.UNLIMITED)
  String getIssueQuoteMessageTemplate();

  void setIssueQuoteMessageTemplate(String issueQuoteMessageTemplate);

  @OneToMany
  AdditionalIssueField[] getAdditionalFields();

  void setAllowedCreateChatLink(boolean allowedCreateChatLink);

  boolean isAllowedCreateChatLink();

  void setAllowedDeleteReplyMessage(boolean aTrue);

  boolean isAllowedDeleteReplyMessage();

  void setProjectId(long projectId);

  @Indexed
  Long getProjectId();

  void setAllowedHandleCfValues(boolean allowedHandleCfValues);

  boolean isAllowedHandleCfValues();
}
