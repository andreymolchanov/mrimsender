/* (C)2021 */
package ru.mail.jira.plugins.myteam.component;

import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.ConvertTemporaryAttachmentParams;
import com.atlassian.jira.issue.attachment.TemporaryAttachmentId;
import com.atlassian.jira.user.ApplicationUser;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.commons.SentryClient;
import ru.mail.jira.plugins.myteam.bot.events.ChatMessageEvent;
import ru.mail.jira.plugins.myteam.commons.exceptions.MyteamServerErrorException;
import ru.mail.jira.plugins.myteam.myteam.MyteamApiClient;
import ru.mail.jira.plugins.myteam.myteam.dto.parts.*;
import ru.mail.jira.plugins.myteam.myteam.dto.response.FileResponse;

@Slf4j
@Component
public class IssueTextConverter {

  private final UserData userData;
  private final MyteamApiClient myteamApiClient;
  private final AttachmentManager attachmentManager;

  public IssueTextConverter(
      UserData userData, MyteamApiClient myteamApiClient, AttachmentManager attachmentManager) {
    this.userData = userData;
    this.myteamApiClient = myteamApiClient;
    this.attachmentManager = attachmentManager;
  }

  @Nullable
  public String convertToJiraCommentStyle(
      ChatMessageEvent event, ApplicationUser commentedUser, Issue commentedIssue) {
    List<Part> parts = event.getMessageParts();
    String message = Objects.requireNonNullElse(event.getMessage(), "");
    StringBuilder outPutStrings = new StringBuilder(message);
    if (parts != null) {
      parts.forEach(
          part ->
              addMentionsAndAttachmentsToIssue(
                  part, outPutStrings, commentedUser, commentedIssue, message));
    }

    return ru.mail.jira.plugins.myteam.commons.Utils.removeAllEmojis(outPutStrings.toString());
  }

  public String convertToJiraDescriptionAndCommentMarkdownStyle(
      Part part, Issue issue, final Function<String, String> messageTextFormattingFunction) {
    List<Part> messageParts =
        part instanceof Reply
            ? ((Reply) part).getMessage().getParts()
            : ((Forward) part).getMessage().getParts();
    String text =
        part instanceof Reply
            ? messageTextFormattingFunction.apply(((Reply) part).getMessage().getText())
            : messageTextFormattingFunction.apply(((Forward) part).getMessage().getText());
    StringBuilder outPutStrings = new StringBuilder();
    if (messageParts != null) {
      outPutStrings.append(text);
      messageParts.forEach(
          messagePart -> {
            CommentaryParts currentPartClass =
                CommentaryParts.fromPartClass(messagePart.getClass());
            if (currentPartClass == null) {
              return;
            }

            String formattedText = outPutStrings.toString();
            switch (currentPartClass) {
              case File:
                File file = (File) messagePart;
                try {
                  log.info("file id {} from event", file.getFileId());
                  HttpResponse<FileResponse> response = myteamApiClient.getFile(file.getFileId());
                  FileResponse fileInfo = response.getBody();
                  log.info("file url {} for load file from VK Teams", fileInfo.getUrl());
                  try (InputStream attachment = myteamApiClient.loadUrlFile(fileInfo.getUrl())) {
                    boolean isUploaded =
                        uploadAttachment(attachment, fileInfo, issue.getReporterUser(), issue);
                    outPutStrings.setLength(0);
                    if (isUploaded) {
                      outPutStrings.append(
                          buildAttachmentLink(
                              file.getFileId(),
                              fileInfo.getType(),
                              fileInfo.getFilename(),
                              formattedText));
                    } else {
                      outPutStrings.append(formattedText);
                    }
                  }
                } catch (UnirestException | IOException | MyteamServerErrorException e) {
                  SentryClient.capture(e);
                  log.error("Unable to add attachment to Issue {}", issue.getKey(), e);
                } catch (Exception e) {
                  outPutStrings.append(file.getFileId());
                  SentryClient.capture(e);
                  log.error(
                      String.format(
                          "Unresolved exception by loading file with id %s for issue with key %s",
                          file.getFileId(), issue.getKey()),
                      e);
                }
                break;
              case Mention:
                Mention mention = (Mention) messagePart;
                ApplicationUser user = userData.getUserByMrimLogin(mention.getUserId());
                outPutStrings.setLength(0);
                if (user != null) {
                  outPutStrings.append(
                      replaceMention(formattedText, mention.getUserId(), user.getName()));
                } else {
                  outPutStrings.append(
                      replaceMention(formattedText, mention.getUserId(), mention.getFirstName()));
                }
                break;
              default:
                break;
            }
          });
    } else {
      outPutStrings.append(text);
    }
    return outPutStrings.toString();
  }

  private boolean uploadAttachment(
      InputStream attachment, FileResponse fileInfo, ApplicationUser user, Issue issue) {
    try {
      TemporaryAttachmentId tmpAttachmentId =
          attachmentManager.createTemporaryAttachment(attachment, fileInfo.getSize());
      ConvertTemporaryAttachmentParams params =
          ConvertTemporaryAttachmentParams.builder()
              .setTemporaryAttachmentId(tmpAttachmentId)
              .setAuthor(user)
              .setIssue(issue)
              .setFilename(fileInfo.getFilename())
              .setContentType(fileInfo.getType())
              .setCreatedTime(DateTime.now())
              .setFileSize(fileInfo.getSize())
              .build();
      attachmentManager.convertTemporaryAttachment(params);
      return true;
    } catch (Exception e) {
      SentryClient.capture(e);
      log.error(e.getLocalizedMessage(), e);
      return false;
    }
  }

  private String replaceMention(CharSequence text, String userId, String userName) {
    return Pattern.compile("@\\[" + userId + "]").matcher(text).replaceAll("[~" + userName + "]");
  }

  private String buildAttachmentLink(
      String fileId, String fileType, String fileName, @Nullable String text) {
    String linkFormat = fileType.equals("image") ? "!%s|thumbnail!\n" : "[^%s]\n";
    if (text == null) {
      return String.format(linkFormat, fileName);
    } else {
      Matcher matcher = Pattern.compile("(https?://.*/get/" + fileId + ").*").matcher(text);
      String myteamFileUrl = StringUtils.EMPTY;
      while (matcher.find()) {
        myteamFileUrl = matcher.group(1);
      }
      return String.format(
          "%s\n%s", matcher.replaceAll(String.format(linkFormat, fileName)), myteamFileUrl);
    }
  }

  public String convertToJiraCommentStyle(
      ChatMessageEvent event,
      String mainMessageTextWithFormattedUnmaskedUrls,
      ApplicationUser commentAuthor,
      Issue issueToComment) {
    List<Part> parts = event.getMessageParts();
    StringBuilder outPutStrings = new StringBuilder(mainMessageTextWithFormattedUnmaskedUrls);
    if (parts != null) {
      parts.stream()
          .filter(part -> part instanceof File || part instanceof Mention)
          .forEach(
              part ->
                  addMentionsAndAttachmentsToIssue(
                      part,
                      outPutStrings,
                      commentAuthor,
                      issueToComment,
                      mainMessageTextWithFormattedUnmaskedUrls));
    }
    return outPutStrings.toString();
  }

  private void addMentionsAndAttachmentsToIssue(
      Part part,
      StringBuilder outPutStrings,
      ApplicationUser commentAuthor,
      Issue issueToComment,
      String message) {
    CommentaryParts currentPartClass = CommentaryParts.fromPartClass(part.getClass());
    if (currentPartClass == null) {
      return;
    }

    switch (currentPartClass) {
      case File:
        File file = (File) part;
        try {
          log.info("file id {} from event", file.getFileId());
          HttpResponse<FileResponse> response = myteamApiClient.getFile(file.getFileId());
          FileResponse fileInfo = response.getBody();
          log.info("file url {} for load file from VK Teams", fileInfo.getUrl());
          try (InputStream attachment = myteamApiClient.loadUrlFile(fileInfo.getUrl())) {
            boolean isUploaded =
                uploadAttachment(attachment, fileInfo, commentAuthor, issueToComment);
            if (isUploaded) {
              outPutStrings.setLength(0);
              outPutStrings.append(
                  buildAttachmentLink(
                      file.getFileId(), fileInfo.getType(), fileInfo.getFilename(), null));
              outPutStrings.append(message);
            }
            if (fileInfo.getType().equals("image")) {
              outPutStrings.append(
                  String.format(
                      "https://files-n.internal.myteam.mail.ru/get/%s\n", file.getFileId()));
            }
            if (file.getCaption() != null) {
              outPutStrings.append(String.format("%s\n", file.getCaption()));
            }
          }
        } catch (UnirestException | IOException | MyteamServerErrorException e) {
          SentryClient.capture(e);
          log.error(
              "Unable to create attachment for comment on Issue {}", issueToComment.getKey(), e);
        } catch (Exception e) {
          outPutStrings.append(file.getFileId());
          SentryClient.capture(e);
          log.error(
              String.format(
                  "Unresolved exception by loading file with id %s for issue with key %s",
                  file.getFileId(), issueToComment.getKey()),
              e);
        }
        break;
      case Mention:
        Mention mention = (Mention) part;
        ApplicationUser user = userData.getUserByMrimLogin(mention.getUserId());
        if (user != null) {
          String replacedMention =
              replaceMention(outPutStrings, mention.getUserId(), user.getName());
          outPutStrings.setLength(0);
          outPutStrings.append(replacedMention);
        } else {
          String errorMessage =
              String.format(
                  "Unable change Myteam mention to Jira's mention, because can't find user with id: %s",
                  mention.getUserId());
          SentryClient.capture(errorMessage);
          log.error(errorMessage);
        }
        break;
      default:
        break;
    }
  }

  public String replaceChatUserMentionToJiraUserMention(
      String messageText, ChatMessageEvent event) {
    if (event.getMessageParts() == null || event.isHasForwards()) {
      return messageText;
    }
    if (!event.isHasMentions()) {
      return messageText;
    }

    StringBuilder outPutStrings = new StringBuilder(messageText);
    for (Part messagePart : event.getMessageParts()) {
      if (!(messagePart instanceof Mention)) {
        continue;
      }
      Mention mention = (Mention) messagePart;
      ApplicationUser user = userData.getUserByMrimLogin(mention.getUserId());
      if (user != null) {
        String replacedMention = replaceMention(outPutStrings, mention.getUserId(), user.getName());
        outPutStrings.setLength(0);
        outPutStrings.append(replacedMention);
      }
    }
    return outPutStrings.toString();
  }

  public IssueTextConverter.@Nullable AttachUploadInfo attachFileToIssue(
      final Issue issue, final File file, final ApplicationUser author) {
    final HttpResponse<FileResponse> response;
    try {
      response = myteamApiClient.getFile(file.getFileId());
    } catch (MyteamServerErrorException e) {
      SentryClient.capture(e);
      return null;
    }

    final FileResponse fileInfo = response.getBody();
    log.info("file url {} for load file from VK Teams", fileInfo.getUrl());
    try (InputStream attachment = myteamApiClient.loadUrlFile(fileInfo.getUrl())) {
      boolean uploaded = uploadAttachment(attachment, fileInfo, author, issue);
      if (uploaded) {
        return new AttachUploadInfo(fileInfo.getFilename(), true);
      } else {
        return new AttachUploadInfo(fileInfo.getFilename(), false);
      }
    } catch (Exception e) {
      SentryClient.capture(
          e,
          null,
          Map.of("userEmail", author.getEmailAddress(), "fileName", fileInfo.getFilename()));
      return new AttachUploadInfo(fileInfo.getFilename(), false);
    }
  }

  @RequiredArgsConstructor
  @Getter
  public static final class AttachUploadInfo {
    private final String fileName;
    private final boolean attached;
  }
}
