/* (C)2021 */
package ru.mail.jira.plugins.myteam.rulesengine.models;

import lombok.Getter;

public enum StateActionRuleType implements RuleType {
  JqlInput("jqlInput");

  @Getter(onMethod_ = {@Override})
  private final String name;

  StateActionRuleType(String name) {
    this.name = name;
  }

  @Override
  public boolean equalsName(String otherName) {
    return name.equals(otherName);
  }
}
