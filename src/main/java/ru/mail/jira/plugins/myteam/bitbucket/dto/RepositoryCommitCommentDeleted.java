/* (C)2021 */
package ru.mail.jira.plugins.myteam.bitbucket.dto;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import ru.mail.jira.plugins.myteam.bitbucket.BitbucketWebhookEvent;
import ru.mail.jira.plugins.myteam.bitbucket.dto.utils.CommentDto;
import ru.mail.jira.plugins.myteam.bitbucket.dto.utils.RepositoryDto;
import ru.mail.jira.plugins.myteam.bitbucket.dto.utils.UserDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class RepositoryCommitCommentDeleted extends BitbucketEventDto
    implements BitbucketWebhookEvent {
  private UserDto actor;
  private CommentDto comment;
  private RepositoryDto repository;

  @JsonProperty("commit")
  private String commitHash;

  @Override
  public String getProjectKey() {
    return repository.getProject().getKey();
  }

  @Override
  public String getRepoSlug() {
    return repository.getSlug();
  }
}