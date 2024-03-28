/* (C)2021 */
package ru.mail.jira.plugins.myteam.service.impl;

import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.bc.issue.comment.CommentService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.exception.IssueNotFoundException;
import com.atlassian.jira.exception.IssuePermissionException;
import com.atlassian.jira.exception.ParseException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.thread.JiraThreadLocalUtils;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.WorkflowActionsBean;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.query.Query;
import com.opensymphony.workflow.loader.ActionDescriptor;
import java.util.*;
import java.util.stream.Collectors;
import javax.naming.NoPermissionException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.mail.jira.plugins.myteam.bot.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.AssigneeChangeValidationException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.IssueTransitionException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.IssueWatchingException;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.ProjectBannedException;
import ru.mail.jira.plugins.myteam.commons.exceptions.ValidationException;
import ru.mail.jira.plugins.myteam.component.IssueTextConverter;
import ru.mail.jira.plugins.myteam.component.UserData;
import ru.mail.jira.plugins.myteam.component.comment.create.CommentCreateArg;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.File;
import ru.mail.jira.plugins.myteam.service.IssueService;
import ru.mail.jira.plugins.myteam.service.PluginData;

@Service
public class IssueServiceImpl implements IssueService {
  private final WorkflowActionsBean workflowActionsBean;
  private final com.atlassian.jira.bc.issue.IssueService jiraIssueService;
  private final IssueManager issueManager;
  private final PermissionManager permissionManager;
  private final WatcherManager watcherManager;
  private final SearchService searchService;
  private final CommentService commentService;
  private final ProjectManager projectManager;
  private final IssueTypeSchemeManager issueTypeSchemeManager;
  private final IssueTypeManager issueTypeManager;
  private final JiraAuthenticationContext jiraAuthenticationContext;
  private final IssueTextConverter issueTextConverter;
  private final IssueWorkflowManager issueWorkflowManager;
  private final CustomFieldManager customFieldManager;
  private final UserData userData;
  private final PluginData pluginData;
  private final String JIRA_BASE_URL;

  public IssueServiceImpl(
      @ComponentImport com.atlassian.jira.bc.issue.IssueService jiraIssueService,
      @ComponentImport IssueManager issueManager,
      @ComponentImport PermissionManager permissionManager,
      @ComponentImport WatcherManager watcherManager,
      @ComponentImport SearchService searchService,
      @ComponentImport CommentService commentService,
      @ComponentImport ProjectManager projectManager,
      @ComponentImport IssueTypeSchemeManager issueTypeSchemeManager,
      @ComponentImport IssueTypeManager issueTypeManager,
      @ComponentImport JiraAuthenticationContext jiraAuthenticationContext,
      @ComponentImport IssueWorkflowManager issueWorkflowManager,
      @ComponentImport CustomFieldManager customFieldManager,
      @ComponentImport ApplicationProperties applicationProperties,
      UserData userData,
      IssueTextConverter issueTextConverter,
      PluginData pluginData) {
    this.jiraIssueService = jiraIssueService;
    this.issueManager = issueManager;
    this.permissionManager = permissionManager;
    this.watcherManager = watcherManager;
    this.searchService = searchService;
    this.commentService = commentService;
    this.projectManager = projectManager;
    this.issueTypeSchemeManager = issueTypeSchemeManager;
    this.issueTypeManager = issueTypeManager;
    this.jiraAuthenticationContext = jiraAuthenticationContext;
    this.issueWorkflowManager = issueWorkflowManager;
    this.customFieldManager = customFieldManager;
    this.userData = userData;
    this.issueTextConverter = issueTextConverter;
    this.pluginData = pluginData;
    this.JIRA_BASE_URL = applicationProperties.getString(APKeys.JIRA_BASEURL);
    this.workflowActionsBean = new WorkflowActionsBean();
  }

  @Override
  public Issue getIssueByUser(String issueKey, @Nullable ApplicationUser user) {
    ApplicationUser contextPrevUser = jiraAuthenticationContext.getLoggedInUser();
    try {
      jiraAuthenticationContext.setLoggedInUser(user); // TODO FIX THREAD CONTEXT
      Issue issue = issueManager.getIssueByKeyIgnoreCase(issueKey);
      if (issue != null) {
        if (permissionManager.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, user)) {
          return issue;
        } else
          throw new IssuePermissionException(
              String.format("User has no permissions to view issue %s", issueKey));
      } else throw new IssueNotFoundException(String.format("Issue %s not found", issueKey));
    } finally {
      jiraAuthenticationContext.setLoggedInUser(contextPrevUser);
    }
  }

  @Override
  public void updateIssue(ApplicationUser logginedInUser, MutableIssue mutableIssue, boolean b) {
    issueManager.updateIssue(
        jiraAuthenticationContext.getLoggedInUser(),
        mutableIssue,
        EventDispatchOption.DO_NOT_DISPATCH,
        b);
  }

  @Override
  public boolean isUserWatching(Issue issue, ApplicationUser user) {
    return watcherManager.isWatching(user, issue);
  }

  @Override
  public String getJiraBaseUrl() {
    return JIRA_BASE_URL;
  }

  @Override
  public SearchResults<Issue> searchByJql(String jql, ApplicationUser user, int page, int pageSize)
      throws SearchException, ParseException {
    JiraThreadLocalUtils.preCall();

    SearchService.ParseResult parseResult = searchService.parseQuery(user, jql);
    if (parseResult.isValid()) {

      Query jqlQuery = parseResult.getQuery();
      Query sanitizedJql = searchService.sanitiseSearchQuery(user, jqlQuery);
      PagerFilter<Issue> pagerFilter = new PagerFilter<>(page * pageSize, pageSize);
      SearchResults<Issue> res = searchService.search(user, sanitizedJql, pagerFilter);

      JiraThreadLocalUtils.postCall();
      return res;
    } else {
      JiraThreadLocalUtils.postCall();
      throw new ParseException("Incorrect jql expression");
    }
  }

  @Override
  public SearchResults<Issue> searchByJqlQuery(
      Query jqlQuery, ApplicationUser user, int page, int pageSize)
      throws SearchException, ParseException {
    JiraThreadLocalUtils.preCall();
    PagerFilter<Issue> pagerFilter = new PagerFilter<>(page * pageSize, pageSize);
    SearchResults<Issue> results = searchService.search(user, jqlQuery, pagerFilter);
    JiraThreadLocalUtils.postCall();
    return results;
  }

  @Override
  public void watchIssue(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException {
    Issue issue = getIssueByUser(issueKey, user);
    if (watcherManager.isWatching(user, issue)) {
      throw new IssueWatchingException(
          String.format("Issue with key %s already watched", issueKey));
    } else {
      watcherManager.startWatching(user, issue);
    }
  }

  @Override
  public void watchIssue(Issue issue, ApplicationUser user) {
    if (!watcherManager.isWatching(user, issue)) {
      watcherManager.startWatching(user, issue);
    }
  }

  @Override
  public void setCustomFieldValue(MutableIssue issue, String userFieldId, ApplicationUser user) {
    CustomField customField = customFieldManager.getCustomFieldObject(userFieldId);
    if (customField != null) {
      CustomFieldType customFieldType = customField.getCustomFieldType();
      if (customFieldType instanceof com.atlassian.jira.issue.customfields.impl.UserCFType) {
        issue.setCustomFieldValue(customField, user);
        updateIssue(jiraAuthenticationContext.getLoggedInUser(), issue, false);
      }
      if (customFieldType instanceof com.atlassian.jira.issue.customfields.impl.MultiUserCFType) {
        List<ApplicationUser> users =
            (List<ApplicationUser>) issue.getCustomFieldValue(customField);
        if (!users.contains(user)) {
          users.add(user);
          issue.setCustomFieldValue(customField, users);
          updateIssue(jiraAuthenticationContext.getLoggedInUser(), issue, false);
        }
      }
    }
  }

  @Override
  public void setAssigneeIssue(MutableIssue issue, ApplicationUser user) {
    issue.setAssignee(user);
    updateIssue(jiraAuthenticationContext.getLoggedInUser(), issue, false);
  }

  @Override
  public void setReporterIssue(MutableIssue issue, ApplicationUser user) {
    issue.setReporter(user);
    updateIssue(jiraAuthenticationContext.getLoggedInUser(), issue, false);
  }

  @Override
  public void unwatchIssue(String issueKey, @Nullable ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException, IssueWatchingException {
    Issue issue = getIssueByUser(issueKey, user);
    if (!watcherManager.isWatching(user, issue)) {
      throw new IssueWatchingException(
          String.format("Issue with key %s already unwatched", issueKey));
    } else {
      watcherManager.stopWatching(user, issue);
    }
  }

  @Override
  public void commentIssue(
      @Nullable String issueKey, @Nullable ApplicationUser user, ChatMessageEvent event)
      throws NoPermissionException, ValidationException {

    Issue commentedIssue = issueManager.getIssueByCurrentKey(issueKey);
    if (user == null || commentedIssue == null) {
      return;
    }

    checkPermissionOnCreateComment(commentedIssue, user);
    createComment(
        commentedIssue,
        user,
        issueTextConverter.convertToJiraCommentStyle(event, user, commentedIssue));
  }

  @Override
  public Comment commentIssue(@NotNull final CommentCreateArg commentCreateArg)
      throws NoPermissionException, ValidationException {
    checkPermissionOnCreateComment(
        commentCreateArg.getIssueToComment(), commentCreateArg.getCommentAuthor());
    if (StringUtils.isBlank(commentCreateArg.getBody())) {
      throw new ValidationException("Comment body cannot be empty");
    }
    return createComment(
        commentCreateArg.getIssueToComment(),
        commentCreateArg.getCommentAuthor(),
        commentCreateArg.getBody());
  }

  @Override
  public void changeIssueStatus(Issue issue, int transitionId, @Nullable ApplicationUser user)
      throws IssueTransitionException {
    com.atlassian.jira.bc.issue.IssueService.TransitionValidationResult validationResult =
        jiraIssueService.validateTransition(
            user, issue.getId(), transitionId, jiraIssueService.newIssueInputParameters());

    if (!validationResult.isValid()) {
      throw new IssueTransitionException(
          "Error due validation issue transition", validationResult.getErrorCollection());
    }

    com.atlassian.jira.bc.issue.IssueService.IssueResult res =
        jiraIssueService.transition(user, validationResult);

    if (!res.isValid()) {
      throw new IssueTransitionException(
          "Error due changing issue status", validationResult.getErrorCollection());
    }
  }

  @Override
  public List<Project> getAllowedProjects() {
    return projectManager.getProjects().stream()
        .filter(proj -> !isProjectExcluded(proj.getId()))
        .collect(Collectors.toList());
  }

  @Override
  public Project getProject(String projectKey, @Nullable ApplicationUser user)
      throws PermissionException, ProjectBannedException {
    Project selectedProject = projectManager.getProjectByCurrentKeyIgnoreCase(projectKey);
    if (selectedProject == null) {
      throw new PermissionException(user + " can't view project " + projectKey);
    }
    if (isProjectExcluded(selectedProject.getId())) {
      throw new ProjectBannedException(String.format("Project with key %s is banned", projectKey));
    }

    if (!permissionManager.hasPermission(ProjectPermissions.CREATE_ISSUES, selectedProject, user)) {
      throw new PermissionException(user + " can't create issue in project " + projectKey);
    }
    return selectedProject;
  }

  @Override
  public Collection<IssueType> getProjectIssueTypes(
      Project project, @Nullable ApplicationUser user) {
    ApplicationUser contextPrevUser = jiraAuthenticationContext.getLoggedInUser();
    try {
      jiraAuthenticationContext.setLoggedInUser(user);
      return issueTypeSchemeManager.getNonSubTaskIssueTypesForProject(project);
    } finally {
      jiraAuthenticationContext.setLoggedInUser(contextPrevUser);
    }
  }

  @Override
  public IssueType getIssueType(String id) {
    return issueTypeManager.getIssueType(id);
  }

  @Override
  public Collection<ActionDescriptor> getIssueTransitions(
      String issueKey, @Nullable ApplicationUser user) {
    Issue issue = getIssueByUser(issueKey, user);
    List<ActionDescriptor> actions =
        issueWorkflowManager.getSortedAvailableActions(issue, TransitionOptions.defaults(), user);

    return actions.stream()
        .filter(a -> workflowActionsBean.getFieldScreenIdForView(a).isEmpty())
        .collect(Collectors.toList());
  }

  @Override
  public boolean changeIssueAssignee(
      String issueKey, String assigneeMyteamLogin, @Nullable ApplicationUser user)
      throws UserNotFoundException, AssigneeChangeValidationException {
    @Nullable ApplicationUser assignee = userData.getUserByMrimLogin(assigneeMyteamLogin);
    if (assignee == null) {
      throw new UserNotFoundException(assigneeMyteamLogin);
    }
    MutableIssue issue = jiraIssueService.getIssue(user, issueKey).getIssue();

    if (issue == null) {
      throw new IssueNotFoundException();
    }
    JiraThreadLocalUtils.preCall();
    // need here to because issueService use authenticationContext
    ApplicationUser contextPrevUser = jiraAuthenticationContext.getLoggedInUser();
    try {
      jiraAuthenticationContext.setLoggedInUser(user);
      com.atlassian.jira.bc.issue.IssueService.AssignValidationResult assignResult =
          jiraIssueService.validateAssign(user, issue.getId(), assignee.getUsername());

      if (!assignResult.isValid()) {
        throw new AssigneeChangeValidationException(
            "Unable to change issue assignee", assignResult.getErrorCollection());
      }
      return jiraIssueService.assign(user, assignResult).isValid();
    } finally {
      jiraAuthenticationContext.setLoggedInUser(contextPrevUser);
      JiraThreadLocalUtils.postCall();
    }
  }

  @Override
  public Issue getIssue(String issueKey) throws IssueNotFoundException {
    Issue issue = issueManager.getIssueByKeyIgnoreCase(issueKey);
    if (issue == null) {
      throw new IssueNotFoundException(String.format("Issue with key %s was not found", issueKey));
    }
    return issue;
  }

  @Override
  public MutableIssue getMutableIssue(Long issueId) throws IssueNotFoundException {
    MutableIssue issue = issueManager.getIssueObject(issueId);
    if (issue == null) {
      throw new IssueNotFoundException(String.format("Issue with id %s was not found", issueId));
    }
    return issue;
  }

  @Override
  public List<Comment> getIssueComments(String issueKey, ApplicationUser user)
      throws IssuePermissionException, IssueNotFoundException {
    return commentService.getCommentsForUser(user, getIssueByUser(issueKey, user));
  }

  private boolean isProjectExcluded(Long projectId) {
    return pluginData.getExcludingProjectIds().contains(projectId);
  }

  private Comment createComment(
      @NotNull final Issue commentedIssue,
      @NotNull final ApplicationUser user,
      @Nullable final String body)
      throws ValidationException {
    CommentService.CommentParameters commentParameters =
        CommentService.CommentParameters.builder()
            .author(user)
            .body(body)
            .issue(commentedIssue)
            .build();
    CommentService.CommentCreateValidationResult validationResult =
        commentService.validateCommentCreate(user, commentParameters);
    if (validationResult.isValid()) {
      return commentService.create(user, validationResult, true);
    } else {
      StringJoiner joiner = new StringJoiner(" ");
      validationResult.getErrorCollection().getErrorMessages().forEach(joiner::add);
      throw new ValidationException(joiner.toString());
    }
  }

  private void checkPermissionOnCreateComment(
      final Issue issueToComment, final ApplicationUser commentAuthor)
      throws NoPermissionException {
    if (!permissionManager.hasPermission(
        ProjectPermissions.ADD_COMMENTS, issueToComment, commentAuthor)) {
      throw new NoPermissionException(
          "User " + commentAuthor + " can't create comment for issue " + issueToComment.getKey());
    }
  }

  @Override
  public IssueTextConverter.@Nullable AttachUploadInfo attachFileToIssue(
      final Issue issue, final File file, final ApplicationUser author) {
    return issueTextConverter.attachFileToIssue(issue, file, author);
  }
}
