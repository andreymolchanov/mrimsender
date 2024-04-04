/* (C)2024 */
package ru.mail.jira.plugins.myteam.component.event.link;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.myteam.bot.listeners.MyteamEventsListener;
import ru.mail.jira.plugins.myteam.component.UserData;
import ru.mail.jira.plugins.myteam.component.event.AbstractChatMessageSender;
import ru.mail.jira.plugins.myteam.component.event.EventRecipient;
import ru.mail.jira.plugins.myteam.component.event.IssueEventChatMessageButtonBuilder;

@Component
public class IssueLinkEventToVKTeamSender
    extends AbstractChatMessageSender<IssueLinkEventRecipientsData, EventRecipient> {
  private final JiraIssueLinkToChatMessageConverter jiraIssueLinkToChatMessageConverter;

  @Autowired
  public IssueLinkEventToVKTeamSender(
      @ComponentImport final JiraAuthenticationContext jiraAuthenticationContext,
      final MyteamEventsListener myteamEventsListener,
      final UserData userData,
      final IssueEventChatMessageButtonBuilder issueEventChatMessageButtonBuilder,
      final JiraIssueLinkToChatMessageConverter jiraIssueLinkToChatMessageConverter) {
    super(
        jiraAuthenticationContext,
        myteamEventsListener,
        userData,
        issueEventChatMessageButtonBuilder);
    this.jiraIssueLinkToChatMessageConverter = jiraIssueLinkToChatMessageConverter;
  }

  @Override
  public void send(final IssueLinkEventRecipientsData issueLinkEventRecipientsData) {
    final String sourceIssueKey = issueLinkEventRecipientsData.getSourceIssueKey();
    final Set<EventRecipient> eventRecipientsForSourceIssue =
        issueLinkEventRecipientsData.getEventRecipientsForSourceIssue();

    final String destinationIssueKey = issueLinkEventRecipientsData.getDestinationIssueKey();
    final Set<EventRecipient> eventRecipientsForDestinationIssue =
        issueLinkEventRecipientsData.getEventRecipientsForDestinationIssue();

    if (!eventRecipientsForSourceIssue.isEmpty()) {
      final IssueLinkEventToChatMessageData
          issueLinkEventToChatMessageDataForSourceIssueRecipients =
              IssueLinkEventToChatMessageData.of(
                  sourceIssueKey,
                  destinationIssueKey,
                  issueLinkEventRecipientsData.getSourceIssueSummary(),
                  issueLinkEventRecipientsData.getDestinationIssueSummary(),
                  issueLinkEventRecipientsData.getIssueLinkCreatorOrRemover(),
                  issueLinkEventRecipientsData.getLinkTypeName(),
                  issueLinkEventRecipientsData.isLinkCreated());
      super.send(
          eventRecipientsForSourceIssue,
          (eventRecipient) ->
              jiraIssueLinkToChatMessageConverter.convert(
                  issueLinkEventToChatMessageDataForSourceIssueRecipients),
          sourceIssueKey);
    }

    if (!eventRecipientsForDestinationIssue.isEmpty()) {
      final IssueLinkEventToChatMessageData
          issueLinkEventToChatMessageDataForDestinationIssueRecipients =
              IssueLinkEventToChatMessageData.of(
                  sourceIssueKey,
                  destinationIssueKey,
                  issueLinkEventRecipientsData.getSourceIssueSummary(),
                  issueLinkEventRecipientsData.getDestinationIssueSummary(),
                  issueLinkEventRecipientsData.getIssueLinkCreatorOrRemover(),
                  issueLinkEventRecipientsData.getLinkTypeName(),
                  issueLinkEventRecipientsData.isLinkCreated());

      super.send(
          eventRecipientsForDestinationIssue,
          (eventRecipient) ->
              jiraIssueLinkToChatMessageConverter.convert(
                  issueLinkEventToChatMessageDataForDestinationIssueRecipients),
          destinationIssueKey);
    }
  }

  @Override
  protected boolean needButtons() {
    return false;
  }
}
