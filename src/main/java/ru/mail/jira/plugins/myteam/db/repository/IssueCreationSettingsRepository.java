/* (C)2022 */
package ru.mail.jira.plugins.myteam.db.repository;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import net.java.ao.Query;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;
import ru.mail.jira.plugins.commons.dao.PagingAndSortingRepository;
import ru.mail.jira.plugins.myteam.controller.dto.IssueCreationSettingsDto;
import ru.mail.jira.plugins.myteam.db.model.AdditionalIssueField;
import ru.mail.jira.plugins.myteam.db.model.IssueCreationSettings;

@Repository
public class IssueCreationSettingsRepository
    extends PagingAndSortingRepository<IssueCreationSettings, IssueCreationSettingsDto> {

  public IssueCreationSettingsRepository(@ComponentImport ActiveObjects ao) {
    super(ao);
  }

  @Override
  public IssueCreationSettingsDto entityToDto(IssueCreationSettings entity) {
    return new IssueCreationSettingsDto(entity);
  }

  @Override
  public void updateEntityFromDto(IssueCreationSettingsDto dto, IssueCreationSettings entity) {
    ao.executeInTransaction(
        () -> {
          entity.setChatId(dto.getChatId());
          entity.setTag(dto.getTag());
          entity.setIssueTypeId(dto.getIssueTypeId());
          entity.setProjectId(dto.getProjectId());
          entity.setEnabled(dto.getEnabled());
          entity.setCreationByAllMembers(dto.getCreationByAllMembers());
          entity.setReporter(dto.getReporter());
          entity.setAddReporterInWatchers(dto.getAddReporterInWatchers());
          entity.setCreationSuccessTemplate(dto.getCreationSuccessTemplate());
          entity.setIssueSummaryTemplate(dto.getIssueSummaryTemplate());
          entity.setIssueQuoteMessageTemplate(dto.getIssueQuoteMessageTemplate());
          entity.setAssignee(dto.getAssignee());
          entity.setLabels(
              (dto.getLabels() == null || dto.getLabels().size() == 0)
                  ? null
                  : String.join(IssueCreationSettingsDto.LABELS_DELIMITER, dto.getLabels()));
          entity.setAllowedCreateChatLink(Boolean.TRUE.equals(dto.getAllowedCreateChatLink()));
          entity.setAllowedDeleteReplyMessage(
              Boolean.TRUE.equals(dto.getAllowedDeleteReplyMessage()));
          entity.setAllowedHandleCfValues(Boolean.TRUE.equals(dto.getAllowedHandleCfValues()));

          if (dto.getAdditionalFields() != null) {
            Map<String, AdditionalIssueField> entityAdditionalFields = new HashMap<>();

            Arrays.stream(entity.getAdditionalFields())
                .forEach(
                    f -> {
                      if (entityAdditionalFields.containsKey(
                          f.getFieldId())) { // delete duplicates such as checkbox group
                        ao.delete(f);
                      } else {
                        entityAdditionalFields.put(f.getFieldId(), f);
                      }
                    });

            dto.getAdditionalFields()
                .forEach(
                    f -> {
                      if (entityAdditionalFields.containsKey(f.getField())) {
                        AdditionalIssueField field = entityAdditionalFields.get(f.getField());
                        try {
                          field.setValue(
                              URLDecoder.decode(f.getValue(), StandardCharsets.UTF_8.name()));
                        } catch (UnsupportedEncodingException e) {
                          field.setValue(f.getValue());
                        }
                        field.save();
                        entityAdditionalFields.remove(f.getField());
                      } else {
                        AdditionalIssueField field = ao.create(AdditionalIssueField.class);
                        field.setIssueCreationSettings(entity);
                        field.setFieldId(f.getField());
                        try {
                          field.setValue(
                              URLDecoder.decode(f.getValue(), StandardCharsets.UTF_8.name()));
                        } catch (UnsupportedEncodingException e) {
                          field.setValue(f.getValue());
                        }
                        field.save();
                      }
                    });

            entityAdditionalFields.values().forEach(ao::delete);
          }
          return entity;
        });
  }

  @Override
  public @Nullable String mapDbField(@Nullable String s) {
    return null;
  }

  public List<IssueCreationSettings> getSettingsByChatId(String chatId) {

    IssueCreationSettings[] settings =
        ao.find(IssueCreationSettings.class, Query.select().where("CHAT_ID = ?", chatId));

    return Arrays.stream(settings).collect(Collectors.toList());
  }

  public Optional<IssueCreationSettings> getSettingsByChatIdAndTag(String chatId, String tag) {

    IssueCreationSettings[] settings =
        ao.find(
            IssueCreationSettings.class,
            Query.select().where("CHAT_ID = ? AND TAG = ?", chatId, tag));

    if (settings.length == 0) {
      return Optional.empty();
    }

    return Optional.of(settings[0]);
  }

  @Override
  public void deleteById(int id) {
    IssueCreationSettings settings = get(id);

    ao.executeInTransaction(
        () -> {
          ao.deleteWithSQL(AdditionalIssueField.class, "ISSUE_CREATION_SETTINGS_ID = ?", id);
          ao.delete(settings);
          return null;
        });
  }

  public List<IssueCreationSettings> getSettingsByProjectId(final long projectId) {
    IssueCreationSettings[] settings =
        ao.find(IssueCreationSettings.class, Query.select().where("PROJECT_ID = ?", projectId));

    return Arrays.asList(settings);
  }
}
