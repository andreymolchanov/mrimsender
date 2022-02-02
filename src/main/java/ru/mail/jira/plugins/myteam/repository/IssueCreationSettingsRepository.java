package ru.mail.jira.plugins.myteam.repository;

import com.atlassian.activeobjects.external.ActiveObjects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import ru.mail.jira.plugins.commons.dao.PagingAndSortingRepository;
import ru.mail.jira.plugins.myteam.dto.IssueCreationSettingsDto;
import ru.mail.jira.plugins.myteam.model.IssueCreationSettingsEntity;

@Repository
public class IssueCreationSettingsRepository extends PagingAndSortingRepository<IssueCreationSettingsEntity, IssueCreationSettingsDto> {

  public IssueCreationSettingsRepository(ActiveObjects ao) {
    super(ao);
  }

  @Override
  public IssueCreationSettingsDto entityToDto(@NotNull IssueCreationSettingsEntity entity) {
    return new IssueCreationSettingsDto(entity);
  }

  @Override
  public void updateEntityFromDto(@NotNull IssueCreationSettingsDto dto, @NotNull IssueCreationSettingsEntity entity) {
    entity.setIssueTypeId(dto.getIssueTypeId());
    entity.setProjectKey(dto.getProjectKey());
    entity.setEnabled(dto.isEnabled());
  }

  @Override
  public @Nullable String mapDbField(@Nullable String s) {
    return null;
  }
}