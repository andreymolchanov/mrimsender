/* (C)2021 */
package ru.mail.jira.plugins.myteam.service;

import com.atlassian.jira.exception.IssueNotFoundException;
import com.atlassian.jira.exception.IssuePermissionException;
import com.atlassian.jira.exception.ParseException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import java.util.Collection;
import java.util.List;
import javax.naming.NoPermissionException;
import ru.mail.jira.plugins.myteam.protocol.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.exceptions.IssueWatchingException;
import ru.mail.jira.plugins.myteam.rulesengine.models.exceptions.ProjectBannedException;

public interface IssueService {

  Issue getIssueByUser(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException;

  Issue getIssue(String issueKey) throws IssueNotFoundException;

  List<Comment> getIssueComments(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException;

  boolean isUserWatching(Issue issue, ApplicationUser user);

  String getJiraBaseUrl();

  SearchResults<Issue> SearchByJql(String jql, ApplicationUser user, int page, int pageSize)
      throws SearchException, ParseException;

  void watchIssue(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException;

  void watchIssue(Issue issue, ApplicationUser user);

  void unwatchIssue(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException;

  void commentIssue(String issueKey, ApplicationUser user, ChatMessageEvent event)
      throws NoPermissionException;

  List<Project> getAllowedProjects();

  Project getProject(String projectKey, ApplicationUser user)
      throws PermissionException, ProjectBannedException;

  Collection<IssueType> getProjectIssueTypes(Project project, ApplicationUser user);

  IssueType getIssueType(String id);
}