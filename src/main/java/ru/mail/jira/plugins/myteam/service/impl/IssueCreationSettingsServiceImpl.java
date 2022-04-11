/* (C)2022 */
package ru.mail.jira.plugins.myteam.service.impl;

import static ru.mail.jira.plugins.myteam.commons.Const.DEFAULT_ISSUE_CREATION_SUCCESS_TEMPLATE;
import static ru.mail.jira.plugins.myteam.commons.Const.DEFAULT_ISSUE_SUMMARY_TEMPLATE;

import com.atlassian.cache.Cache;
import com.atlassian.cache.CacheManager;
import com.atlassian.cache.CacheSettingsBuilder;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.exception.NotFoundException;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.util.lang.Pair;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.mail.jira.plugins.myteam.controller.dto.IssueCreationSettingsDto;
import ru.mail.jira.plugins.myteam.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.exceptions.SettingsTagAlreadyExistsException;
import ru.mail.jira.plugins.myteam.model.IssueCreationSettings;
import ru.mail.jira.plugins.myteam.myteam.MyteamApiClient;
import ru.mail.jira.plugins.myteam.myteam.dto.chats.ChatInfoResponse;
import ru.mail.jira.plugins.myteam.myteam.dto.chats.GroupChatInfo;
import ru.mail.jira.plugins.myteam.protocol.MessageFormatter;
import ru.mail.jira.plugins.myteam.repository.IssueCreationSettingsRepository;
import ru.mail.jira.plugins.myteam.service.IssueCreationSettingsService;

@Service
@Slf4j
public class IssueCreationSettingsServiceImpl implements IssueCreationSettingsService {

  private static final String SPLITTER = "##";

  private static final String CACHE_NAME =
      IssueCreationSettingsServiceImpl.class.getName() + ".issueSettingsCache";

  private final IssueCreationSettingsRepository issueCreationSettingsRepository;
  private final IssueTypeManager issueTypeManager;
  private final ProjectService projectService;
  private final MyteamApiClient myteamApiClient;
  private final MessageFormatter messageFormatter;

  private final Cache<String, Optional<IssueCreationSettingsDto>> issueSettingsCache;

  public IssueCreationSettingsServiceImpl(
      IssueCreationSettingsRepository issueCreationSettingsRepository,
      MessageFormatter messageFormatter,
      MyteamApiClient myteamApiClient,
      @ComponentImport IssueTypeManager issueTypeManager,
      @ComponentImport ProjectService projectService,
      @ComponentImport CacheManager cacheManager) {
    this.issueCreationSettingsRepository = issueCreationSettingsRepository;
    this.issueTypeManager = issueTypeManager;
    this.projectService = projectService;
    this.messageFormatter = messageFormatter;
    this.myteamApiClient = myteamApiClient;

    issueSettingsCache =
        cacheManager.getCache(
            CACHE_NAME,
            this::getSettingsByChatIdAndTag,
            new CacheSettingsBuilder().remote().replicateViaInvalidation().build());
  }

  @Override
  public List<IssueCreationSettingsDto> getAllSettings() {
    return mapAdditionalSettingsInfos(issueCreationSettingsRepository.findAll());
  }

  @Override
  public IssueCreationSettingsDto getSettings(int id) throws NotFoundException {
    return new IssueCreationSettingsDto(issueCreationSettingsRepository.get(id));
  }

  @Override
  @Nullable
  public IssueCreationSettingsDto getSettingsFromCache(String chatId, String tag) {
    return issueSettingsCache.get(combineKey(chatId, tag)).orElse(null); // NotNull
  }

  @Override
  public List<IssueCreationSettingsDto> getSettingsByProjectKey(String projectKey) {
    Project project = projectService.getProjectByKey(projectKey).getProject();
    if (project == null) {
      return new ArrayList<>();
    }

    List<IssueCreationSettings> set =
        issueCreationSettingsRepository.getSettingsByProjectId(project.getKey());

    return mapAdditionalSettingsInfos(set);
  }

  @Override
  public @NotNull List<IssueCreationSettingsDto> getSettingsByChatId(String chatId) {
    return issueCreationSettingsRepository.getSettingsByChatId(chatId).stream()
        .map(this::mapAdditionalSettingsInfo)
        .collect(Collectors.toList());
  }

  @Override
  public IssueCreationSettingsDto createSettings(@NotNull IssueCreationSettingsDto settings)
      throws SettingsTagAlreadyExistsException {
    checkAlreadyHasTag(settings);
    issueCreationSettingsRepository.create(settings);
    return getSettingsFromCache(settings.getChatId(), settings.getTag());
  }

  @Override
  public IssueCreationSettingsDto updateSettings(int id, @NotNull IssueCreationSettingsDto settings)
      throws SettingsTagAlreadyExistsException {
    checkAlreadyHasTag(settings);
    issueCreationSettingsRepository.update(id, settings);
    issueSettingsCache.remove(combineKey(settings.getChatId(), settings.getTag()));

    return getSettingsFromCache(settings.getChatId(), settings.getTag());
  }

  @Override
  public boolean hasRequiredFields(@Nullable IssueCreationSettingsDto settings) {
    return settings != null
        && settings.getEnabled()
        && settings.getProjectKey() != null
        && settings.getIssueTypeId() != null;
  }

  @Override
  public boolean hasChatSettings(String chatId, String tag) {
    return getSettingsFromCache(chatId, tag) != null;
  }

  @Override
  public IssueCreationSettingsDto getSettingsById(int id) {
    @NotNull IssueCreationSettings settings = issueCreationSettingsRepository.get(id);
    return new IssueCreationSettingsDto(
        settings, messageFormatter.getMyteamLink(settings.getChatId()));
  }

  @Override
  public void deleteSettings(int id) {
    @NotNull IssueCreationSettings settings = issueCreationSettingsRepository.get(id);
    issueCreationSettingsRepository.deleteById(id);
    issueSettingsCache.remove(combineKey(settings.getChatId(), settings.getTag()));
  }

  @Override
  public void updateDefaultSettings(@NotNull IssueCreationSettingsDto settings) {
    if (settings.getIssueSummaryTemplate() == null)
      settings.setIssueSummaryTemplate(DEFAULT_ISSUE_SUMMARY_TEMPLATE);
    if (settings.getCreationSuccessTemplate() == null)
      settings.setCreationSuccessTemplate(DEFAULT_ISSUE_CREATION_SUCCESS_TEMPLATE);
  }

  private void checkAlreadyHasTag(IssueCreationSettingsDto settings)
      throws SettingsTagAlreadyExistsException {
    List<IssueCreationSettings> chatSettings =
        issueCreationSettingsRepository.getSettingsByChatId(settings.getChatId());
    boolean isAlreadyHasTag =
        chatSettings.stream()
            .anyMatch(
                s ->
                    s.getTag().equals(settings.getTag())
                        && s.getChatId().equals(settings.getChatId())
                        && (settings.getId() == null || s.getID() != settings.getId()));

    if (isAlreadyHasTag) {
      throw new SettingsTagAlreadyExistsException(
          String.format("Tag #%s is already exist", settings.getTag()));
    }
  }

  private List<IssueCreationSettingsDto> mapAdditionalSettingsInfos(
      List<IssueCreationSettings> settings) {
    return settings.stream().map(this::mapAdditionalSettingsInfo).collect(Collectors.toList());
  }

  private @NotNull Optional<IssueCreationSettingsDto> getSettingsByChatIdAndTag(
      String chatIdAndTag) {
    Pair<String, String> key = splitKey(chatIdAndTag);
    return issueCreationSettingsRepository
        .getSettingsByChatIdAndTag(key.first(), key.second())
        .map(this::mapAdditionalSettingsInfo);
  }

  private IssueCreationSettingsDto mapAdditionalSettingsInfo(IssueCreationSettings settings) {

    IssueCreationSettingsDto settingsDto =
        new IssueCreationSettingsDto(
            settings, messageFormatter.getMyteamLink(settings.getChatId()));

    try {
      ChatInfoResponse chatInfo = myteamApiClient.getChatInfo(settings.getChatId()).getBody();
      if (chatInfo instanceof GroupChatInfo) {
        settingsDto.setChatTitle(((GroupChatInfo) chatInfo).getTitle());
      }
    } catch (MyteamServerErrorException e) {
      log.error(e.getLocalizedMessage(), e);
    }

    if (StringUtils.isNotEmpty(settings.getIssueTypeId())) {
      IssueType issueType = issueTypeManager.getIssueType(settings.getIssueTypeId());
      if (issueType != null) {
        settingsDto.setIssueTypeName(issueType.getNameTranslation());
      }
    }
    return settingsDto;
  }

  private Pair<String, String> splitKey(String key) {
    List<String> split = Splitter.on(SPLITTER).splitToList(key);
    return Pair.of(split.get(0), split.get(1));
  }

  private String combineKey(String chatId, String tag) {
    return chatId + SPLITTER + tag;
  }
}
