/* (C)2020 */
package ru.mail.jira.plugins.myteam.myteam.dto.parts;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import ru.mail.jira.plugins.myteam.myteam.dto.User;

public class Reply extends Part<Reply.Data> {
  public ReplyMessage getMessage() {
    return this.getPayload().message;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Data {
    public ReplyMessage message;
  }

  @Getter
  @Setter
  @ToString
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ReplyMessage {
    private User from;
    private long msgId;
    private String text;
    private long timestamp;
  }
}
