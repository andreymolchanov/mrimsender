/* (C)2021 */
package ru.mail.jira.plugins.myteam.bitbucket.dto;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import ru.mail.jira.plugins.myteam.bitbucket.BitbucketWebhookEvent;
import ru.mail.jira.plugins.myteam.bitbucket.dto.utils.PullRequestDto;
import ru.mail.jira.plugins.myteam.bitbucket.dto.utils.UserDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class PullRequestMerged extends BitbucketEventDto implements BitbucketWebhookEvent {
  private UserDto actor;
  private PullRequestDto pullRequest;

  @Override
  public String getProjectKey() {
    return pullRequest.getFromRef().getRepository().getProject().getKey();
  }

  @Override
  public String getRepoSlug() {
    return pullRequest.getFromRef().getRepository().getSlug();
  }
}
