/* (C)2020 */
package ru.mail.jira.plugins.myteam.controller;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.JiraServiceContextImpl;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions.LinkIssueWithChatException;
import ru.mail.jira.plugins.myteam.commons.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.component.PermissionHelper;
import ru.mail.jira.plugins.myteam.component.UserData;
import ru.mail.jira.plugins.myteam.controller.dto.ChatCreationDataDto;
import ru.mail.jira.plugins.myteam.controller.dto.ChatMemberDto;
import ru.mail.jira.plugins.myteam.controller.dto.ChatMetaDto;
import ru.mail.jira.plugins.myteam.myteam.MyteamApiClient;
import ru.mail.jira.plugins.myteam.myteam.dto.chats.ChatInfoResponse;
import ru.mail.jira.plugins.myteam.myteam.dto.chats.ChatMember;
import ru.mail.jira.plugins.myteam.protocol.MyteamService;
import ru.mail.jira.plugins.myteam.service.model.MyteamChatMetaDto;

@Controller
@Slf4j
@Path("/chats")
public class ChatCreationController {
  private final JiraAuthenticationContext jiraAuthenticationContext;
  private final MyteamApiClient myteamApiClient;
  private final IssueManager issueManager;
  private final WatcherManager watcherManager;
  private final AvatarService avatarService;
  private final UserData userData;
  private final UserSearchService userSearchService;
  private final MyteamService myteamService;
  private final PermissionHelper permissionHelper;

  @Autowired
  public ChatCreationController(
      @ComponentImport JiraAuthenticationContext jiraAuthenticationContext,
      @ComponentImport IssueManager issueManager,
      @ComponentImport WatcherManager watcherManager,
      @ComponentImport AvatarService avatarService,
      @ComponentImport UserSearchService userSearchService,
      MyteamApiClient myteamApiClient,
      UserData userData,
      MyteamService myteamService,
      PermissionHelper permissionHelper) {
    this.jiraAuthenticationContext = jiraAuthenticationContext;
    this.issueManager = issueManager;
    this.watcherManager = watcherManager;
    this.myteamApiClient = myteamApiClient;
    this.userSearchService = userSearchService;
    this.avatarService = avatarService;
    this.userData = userData;
    this.myteamService = myteamService;
    this.permissionHelper = permissionHelper;
  }

  @GET
  @Path("/chatData/{issueKey}")
  @Produces({MediaType.APPLICATION_JSON})
  @Nullable
  public ChatMetaDto findChatData(@PathParam("issueKey") String issueKey) {

    ApplicationUser loggedInUser = jiraAuthenticationContext.getLoggedInUser();

    if (loggedInUser == null) {
      throw new SecurityException();
    }

    if (!permissionHelper.checkBrowseIssuePermission(loggedInUser, issueKey)) {
      throw new SecurityException();
    }

    MyteamChatMetaDto chatMeta = myteamService.findChatByIssueKey(issueKey);
    if (chatMeta == null) {
      return null;
    }
    /*
    //localhost test stubbing part
    return Response.ok(
            ChatMetaDto.buildChatInfo(
                new GroupChatInfo(
                    "title", "about", "rules", "http:/myteam/inite/link", false, false)))
        .build();*/
    try {
      List<ChatMemberDto> chatMemberDtos;
      ChatMember chatMembersFromApi = myteamApiClient.getMembers(chatMeta.getChatId()).getBody();
      if (chatMembersFromApi != null && chatMembersFromApi.members != null) {
        List<ApplicationUser> applicationUsers =
            chatMembersFromApi.members.stream()
                .map(
                    member ->
                        StreamSupport.stream(
                                userSearchService.findUsersByEmail(member.userId).spliterator(),
                                false)
                            .filter(ApplicationUser::isActive)
                            .findFirst()
                            .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        chatMemberDtos =
            applicationUsers.stream()
                .map(
                    member -> {
                      String url = avatarService.getAvatarURL(loggedInUser, member).toString();
                      return new ChatMemberDto(member.getDisplayName(), member.getId(), url);
                    })
                .collect(Collectors.toList());
      } else {
        chatMemberDtos = Collections.emptyList();
      }

      HttpResponse<ChatInfoResponse> chatInfoResponse =
          myteamApiClient.getChatInfo(chatMeta.getChatId());
      if (chatInfoResponse.getStatus() == 200 && chatInfoResponse.getBody() != null) {
        ChatInfoResponse chatInfo = chatInfoResponse.getBody();
        return ChatMetaDto.buildChatInfo(chatInfo, chatMemberDtos);
      } else {
        log.error(
            "getChatInfo method returns NOT OK status or empty body for chat with chatId = "
                + chatMeta.getChatId());
        throw new RuntimeException(
            "getChatInfo method returns NOT OK status or empty body for chat with chatId = "
                + chatMeta.getChatId());
      }
    } catch (UnirestException | MyteamServerErrorException e) {
      log.error("exception in getChatInfo method call for chatId = " + chatMeta.getChatId(), e);
      throw new RuntimeException(
          "exception in getChatInfo method call for chatId = " + chatMeta.getChatId(), e);
    }
  }

  @GET
  @Path("/chatCreationData/{issueKey}")
  @Produces({MediaType.APPLICATION_JSON})
  @Nullable
  public ChatCreationDataDto getChatCreationData(@PathParam("issueKey") String issueKey) {
    ApplicationUser loggedInUser = jiraAuthenticationContext.getLoggedInUser();
    if (loggedInUser == null) {
      throw new SecurityException();
    }

    Issue currentIssue = issueManager.getIssueByKeyIgnoreCase(issueKey);
    if (currentIssue == null) {
      return null;
    }

    String chatName = String.join(": ", currentIssue.getKey(), currentIssue.getSummary());

    List<ChatMemberDto> availableChatUsers =
        Stream.concat(
                watcherManager.getWatchersUnsorted(currentIssue).stream(),
                Stream.of(loggedInUser, currentIssue.getAssignee(), currentIssue.getReporter()))
            .filter(Objects::nonNull)
            .distinct()
            .filter(userData::isCreateChatsWithUserAllowed)
            .sorted((u1, u2) -> u1.getDisplayName().compareToIgnoreCase(u2.getDisplayName()))
            // TODO max chat members hardcoded here
            .limit(30)
            .map(
                user ->
                    new ChatMemberDto(
                        user.getDisplayName(),
                        user.getId(),
                        avatarService
                            .getAvatarURL(loggedInUser, user, Avatar.Size.LARGE)
                            .toString()))
            .collect(Collectors.toList());

    return new ChatCreationDataDto(chatName, availableChatUsers);
  }

  @POST
  @Path("/createChat/{issueKey}")
  @Produces({MediaType.APPLICATION_JSON})
  @Nullable
  public ChatMetaDto createChat(
      @PathParam("issueKey") String issueKey,
      @FormParam("name") String chatName,
      @FormParam("memberIds") List<Long> memberIds)
      throws LinkIssueWithChatException {
    ApplicationUser loggedInUser = jiraAuthenticationContext.getLoggedInUser();
    if (loggedInUser == null) {
      throw new SecurityException();
    }
    try {
      return myteamService.createChatByJiraUserIds(
          issueKey, chatName, memberIds, loggedInUser, false);
    } catch (LinkIssueWithChatException e) {
      throw new RuntimeException(e);
    }
  }

  @GET
  @Path("/chatCreationData/users")
  @Produces({MediaType.APPLICATION_JSON})
  public List<ChatMemberDto> getAvailableChatMembers(@QueryParam("searchText") String searchText) {
    ApplicationUser loggedInUser = jiraAuthenticationContext.getLoggedInUser();
    if (loggedInUser == null) {
      throw new SecurityException();
    }
    return userSearchService
        .findUsersAllowEmptyQuery(new JiraServiceContextImpl(loggedInUser), searchText)
        .stream()
        .map(
            user ->
                new ChatMemberDto(
                    user.getDisplayName(),
                    user.getId(),
                    avatarService.getAvatarURL(loggedInUser, user, Avatar.Size.LARGE).toString()))
        .collect(Collectors.toList());
  }
}
