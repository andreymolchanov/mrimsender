package ru.mail.jira.plugins.mrimsender.icq.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import ru.mail.jira.plugins.mrimsender.icq.dto.parts.Part;

import java.util.List;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private User from;
    private long msgId;
    private String text;
    private long timestamp;
    private Chat chat;
    private List<Part> parts;

    public List<Part> getParts() {
        return parts;
    }
}
