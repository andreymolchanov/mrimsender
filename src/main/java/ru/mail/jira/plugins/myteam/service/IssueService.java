/* (C)2021 */
package ru.mail.jira.plugins.myteam.service;

import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.exception.IssueNotFoundException;
import com.atlassian.jira.exception.IssuePermissionException;
import com.atlassian.jira.exception.ParseException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.query.Query;
import com.opensymphony.workflow.loader.ActionDescriptor;
import java.util.Collection;
import java.util.List;
import javax.naming.NoPermissionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.bot.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.AssigneeChangeValidationException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.IssueTransitionException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.IssueWatchingException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.ProjectBannedException;
import ru.mail.jira.plugins.myteam.commons.exceptions.ValidationException;
import ru.mail.jira.plugins.myteam.component.IssueTextConverter;
import ru.mail.jira.plugins.myteam.component.comment.create.CommentCreateArg;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.File;

public interface IssueService {

  Issue getIssueByUser(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException;

  void updateIssue(ApplicationUser logginedInUser, MutableIssue mutableIssue, boolean b);

  MutableIssue getMutableIssue(Long issueId) throws IssueNotFoundException;

  Issue getIssue(String issueKey) throws IssueNotFoundException;

  boolean isUserWatching(Issue issue, ApplicationUser user);

  String getJiraBaseUrl();

  SearchResults<Issue> searchByJql(String jql, ApplicationUser user, int page, int pageSize)
      throws SearchException, ParseException;

  SearchResults<Issue> searchByJqlQuery(Query query, ApplicationUser user, int page, int pageSize)
      throws SearchException, ParseException;

  void watchIssue(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException;

  void watchIssue(Issue issue, ApplicationUser user);

  void setCustomFieldValue(MutableIssue issue, String userFieldId, ApplicationUser user);

  void setAssigneeIssue(MutableIssue issue, ApplicationUser user);

  void setReporterIssue(MutableIssue issue, ApplicationUser user);

  void unwatchIssue(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException;

  void commentIssue(
      @Nullable String issueKey, @Nullable ApplicationUser user, ChatMessageEvent event)
      throws NoPermissionException, ValidationException;

  Comment commentIssue(@NotNull final CommentCreateArg commentCreateArg)
      throws NoPermissionException, ValidationException;

  void changeIssueStatus(Issue issue, int transitionId, @Nullable ApplicationUser user)
      throws IssueTransitionException;

  List<Project> getAllowedProjects();

  List<Comment> getIssueComments(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException;

  Project getProject(String projectKey, @Nullable ApplicationUser user)
      throws PermissionException, ProjectBannedException;

  Collection<IssueType> getProjectIssueTypes(Project project, @Nullable ApplicationUser user);

  IssueType getIssueType(String id);

  Collection<ActionDescriptor> getIssueTransitions(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException;

  boolean changeIssueAssignee(String issueKey, String userMention, @Nullable ApplicationUser user)
      throws UserNotFoundException, AssigneeChangeValidationException;

  IssueTextConverter.@Nullable AttachUploadInfo attachFileToIssue(
      final Issue issue, final File file, final ApplicationUser author);
}
