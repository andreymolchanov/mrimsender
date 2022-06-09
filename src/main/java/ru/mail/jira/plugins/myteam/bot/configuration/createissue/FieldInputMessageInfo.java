/* (C)2022 */
package ru.mail.jira.plugins.myteam.bot.configuration.createissue;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.myteam.dto.InlineKeyboardMarkupButton;

@Getter
@Builder
@SuppressWarnings("MissingSummary")
public class FieldInputMessageInfo {
  private final String message;
  @Nullable private final List<List<InlineKeyboardMarkupButton>> buttons;
}