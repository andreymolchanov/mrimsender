/* (C)2022 */
package ru.mail.jira.plugins.myteam.configuration.createissue;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ru.mail.jira.plugins.myteam.myteam.dto.InlineKeyboardMarkupButton;

@Getter
@Builder
public class FieldInputMessageInfo {
  private final String message;
  @Nullable private final List<List<InlineKeyboardMarkupButton>> buttons;
}
