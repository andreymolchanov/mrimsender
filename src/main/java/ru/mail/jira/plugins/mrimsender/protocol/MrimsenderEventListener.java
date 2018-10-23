package ru.mail.jira.plugins.mrimsender.protocol;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.MentionIssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.notification.JiraNotificationReason;
import com.atlassian.jira.notification.NotificationFilterContext;
import com.atlassian.jira.notification.NotificationFilterManager;
import com.atlassian.jira.notification.NotificationRecipient;
import com.atlassian.jira.notification.NotificationSchemeManager;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.scheme.SchemeEntity;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.mail.jira.plugins.mrimsender.configuration.PluginData;
import ru.mail.jira.plugins.mrimsender.configuration.UserData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MrimsenderEventListener implements InitializingBean, DisposableBean {

    private static final Logger log = Logger.getLogger(MrimsenderEventListener.class);

    private final EventPublisher eventPublisher;
    private final GroupManager groupManager;
    private final NotificationFilterManager notificationFilterManager;
    private final NotificationSchemeManager notificationSchemeManager;
    private final PermissionManager permissionManager;
    private final PluginData pluginData;
    private final ProjectRoleManager projectRoleManager;
    private final UserData userData = new UserData();

    public MrimsenderEventListener(EventPublisher eventPublisher, GroupManager groupManager, NotificationFilterManager notificationFilterManager, NotificationSchemeManager notificationSchemeManager, PermissionManager permissionManager, PluginData pluginData, ProjectRoleManager projectRoleManager) {
        this.eventPublisher = eventPublisher;
        this.groupManager = groupManager;
        this.notificationFilterManager = notificationFilterManager;
        this.notificationSchemeManager = notificationSchemeManager;
        this.permissionManager = permissionManager;
        this.pluginData = pluginData;
        this.projectRoleManager = projectRoleManager;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        MrimsenderThread.startInstance();
        eventPublisher.register(this);
    }

    @Override
    public void destroy() throws Exception {
        MrimsenderThread.stopInstance();
        eventPublisher.unregister(this);
    }

    private void sendMessage(Collection<ApplicationUser> recipients, Object event) {
        if (!StringUtils.isEmpty(pluginData.getLogin()) && !StringUtils.isEmpty(pluginData.getPassword()))
            for (ApplicationUser recipient : recipients) {
                if (recipient.isActive() && userData.isEnabled(recipient)) {
                    String mrimLogin = userData.getMrimLogin(recipient);
                    if (StringUtils.isNotBlank(mrimLogin)) {
                        String message = null;
                        if (event instanceof IssueEvent)
                            message = new MessageFormatter(recipient).formatEvent((IssueEvent) event);
                        if (event instanceof MentionIssueEvent)
                            message = new MessageFormatter(recipient).formatEvent((MentionIssueEvent) event);
                        if (message != null)
                            MrimsenderThread.sendMessage(mrimLogin, message);
                    }
                }
            }
    }

    @SuppressWarnings("unused")
    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        try {
            if (issueEvent.isSendMail()) {
                Set<ApplicationUser> recipients = new HashSet<>();

                Set<NotificationRecipient> notificationRecipients = notificationSchemeManager.getRecipients(issueEvent);
                NotificationFilterContext context = notificationFilterManager.makeContextFrom(JiraNotificationReason.ISSUE_EVENT, issueEvent);
                for (SchemeEntity schemeEntity : notificationSchemeManager.getNotificationSchemeEntities(issueEvent.getProject(), issueEvent.getEventTypeId())) {
                    context = notificationFilterManager.makeContextFrom(context, com.atlassian.jira.notification.type.NotificationType.from(schemeEntity.getType()));
                    Set<NotificationRecipient> recipientsFromScheme = notificationSchemeManager.getRecipients(issueEvent, schemeEntity);
                    recipientsFromScheme = Sets.newHashSet(notificationFilterManager.recomputeRecipients(recipientsFromScheme, context));
                    notificationRecipients.addAll(recipientsFromScheme);
                }

                for (NotificationRecipient notificationRecipient : notificationRecipients) {
                    if (canSendEventToUser(notificationRecipient.getUser(), issueEvent))
                        recipients.add(notificationRecipient.getUser());
                }

                sendMessage(recipients, issueEvent);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private boolean canSendEventToUser(ApplicationUser user, IssueEvent issueEvent) {
        ProjectRole projectRole = null;
        String groupName = null;
        Issue issue = issueEvent.getIssue();
        if (issueEvent.getWorklog() != null) {
            projectRole = issueEvent.getWorklog().getRoleLevel();
            groupName = issueEvent.getWorklog().getGroupLevel();
        } else if (issueEvent.getComment() != null) {
            projectRole = issueEvent.getComment().getRoleLevel();
            groupName = issueEvent.getComment().getGroupLevel();
        }

        if (!permissionManager.hasPermission(ProjectPermissions.BROWSE_PROJECTS, issue, user))
            return false;
        if (groupName != null && !groupManager.isUserInGroup(user, groupName))
            return false;
        if (projectRole != null && !projectRoleManager.isUserInProjectRole(user, projectRole, issue.getProjectObject()))
            return false;

        return true;
    }

    @SuppressWarnings("unused")
    @EventListener
    public void onMentionIssueEvent(MentionIssueEvent mentionIssueEvent) {
        try {
            List<ApplicationUser> recipients = new ArrayList<>();
            for (ApplicationUser user : mentionIssueEvent.getToUsers())
                if (!mentionIssueEvent.getCurrentRecipients().contains(new NotificationRecipient(user)))
                    recipients.add(user);
            sendMessage(recipients, mentionIssueEvent);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}