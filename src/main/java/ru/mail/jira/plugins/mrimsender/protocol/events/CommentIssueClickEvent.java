package ru.mail.jira.plugins.mrimsender.protocol.events;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public class CommentIssueClickEvent implements Event {
    private final String userId;
    private final String chatId;
    private final String queryId;
    private final String issueKey;

    public CommentIssueClickEvent(ButtonClickEvent chatButtonClickEvent) {
        this.userId = chatButtonClickEvent.getUserId();
        this.chatId = chatButtonClickEvent.getChatId();
        this.queryId = chatButtonClickEvent.getQueryId();
        this.issueKey = StringUtils.substringAfter(chatButtonClickEvent.getCallbackData(), "-");
    }
}
