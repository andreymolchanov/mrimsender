package ru.mail.jira.plugins.mrimsender.protocol.events;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class ViewIssueClickEvent implements Event {
    private final String issueKey;
    private final String userId;
    private final String chatId;
    private final String queryId;

    public ViewIssueClickEvent(ButtonClickEvent chatButtonClickEvent) {
        this.issueKey = StringUtils.substringAfter(chatButtonClickEvent.getCallbackData(), "-");;
        this.userId = chatButtonClickEvent.getUserId();
        this.chatId = chatButtonClickEvent.getChatId();
        this.queryId = chatButtonClickEvent.getQueryId();
    }
}
