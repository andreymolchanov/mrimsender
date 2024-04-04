/* (C)2022 */
package ru.mail.jira.plugins.myteam.bot.rulesengine.models.exceptions;

import com.atlassian.jira.util.ErrorCollection;
import java.util.stream.Collectors;
import lombok.Getter;

public abstract class ErrorCollectionException extends Exception {
  @Getter private final ErrorCollection errors;

  public ErrorCollectionException(String message, ErrorCollection errors) {

    super(
        String.format(
            "%s%n%s%n%s%n%n%s",
            message,
            errors.getReasons().stream().map(Enum::name).collect(Collectors.joining("\n")),
            String.join("\n", errors.getErrorMessages()),
            errors.getErrors().keySet().stream()
                .map(e -> String.format("%s: %s", e, errors.getErrors().get(e)))
                .collect(Collectors.joining("\n"))));
    this.errors = errors;
  }
}
