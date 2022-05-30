/* (C)2020 */
package ru.mail.jira.plugins.myteam.myteam.dto.chats;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateChatResponse {
  private String sn;
}
