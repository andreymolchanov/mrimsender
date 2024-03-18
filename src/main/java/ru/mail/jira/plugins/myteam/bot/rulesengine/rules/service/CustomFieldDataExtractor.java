/* (C)2024 */
package ru.mail.jira.plugins.myteam.bot.rulesengine.rules.service;

import com.atlassian.jira.issue.fields.Field;
import com.google.common.base.Function;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import javax.annotation.concurrent.NotThreadSafe;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.mail.jira.plugins.myteam.service.IssueCreationService;

@Getter
@NotThreadSafe
public class CustomFieldDataExtractor implements Function<Matcher, String> {
  private final List<CustomFieldData> customFieldData = new ArrayList<>();
  private final IssueCreationService issueCreationService;

  public CustomFieldDataExtractor(final IssueCreationService issueCreationService) {
    this.issueCreationService = issueCreationService;
  }

  @Override
  public String apply(final Matcher matcher) {
    final String cfId = matcher.group(1).replace("#cf", "");
    final Field field =
        Objects.requireNonNull(
            issueCreationService.getField("customfield_" + cfId),
            "Custom field not found by id " + cfId);
    final String cfValue = matcher.group(2).substring(0, matcher.group(2).length() - 1);
    customFieldData.add(new CustomFieldData(field, cfValue));
    return "";
  }

  @RequiredArgsConstructor
  @Getter
  public static class CustomFieldDataAndTextForResolvingSummary {
    private final List<CustomFieldData> customFieldData;
    private final String textForResolvingSummary;
  }

  @RequiredArgsConstructor
  @Getter
  public static class CustomFieldData {
    private final Field field;
    private final String cfValue;
  }
}
