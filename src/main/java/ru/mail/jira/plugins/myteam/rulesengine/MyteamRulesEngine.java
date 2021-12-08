/* (C)2021 */
package ru.mail.jira.plugins.myteam.rulesengine;

import org.jeasy.rules.api.*;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.stereotype.Component;
import ru.mail.jira.plugins.myteam.myteam.dto.ChatType;
import ru.mail.jira.plugins.myteam.protocol.events.MyteamEvent;
import ru.mail.jira.plugins.myteam.rulesengine.models.RuleType;
import ru.mail.jira.plugins.myteam.rulesengine.states.BotState;
import ru.mail.jira.plugins.myteam.rulesengine.states.EmptyState;

@Component
public class MyteamRulesEngine {

  private final Rules rules;
  private final RulesEngine rulesEngine;

  public MyteamRulesEngine() {
    rulesEngine =
        new DefaultRulesEngine(
            new RulesEngineParameters(
                true, false, false, RulesEngineParameters.DEFAULT_RULE_PRIORITY_THRESHOLD));
    rules = new Rules();
  }

  public void registerRule(Object rule) {
    rules.register(rule);
  }

  public void fire(Facts facts) {
    rulesEngine.fire(rules, facts);
  }

  public static Facts formCommandFacts(RuleType command, MyteamEvent event) {
    return formCommandFacts(command.getName(), event, "");
  }

  public static Facts formCommandFacts(RuleType command, MyteamEvent event, String args) {
    return formCommandFacts(command.getName(), event, args);
  }

  public static Facts formCommandFacts(String command, MyteamEvent event, String args) {
    Facts facts = formBasicsFacts(command, event);
    facts.add(new Fact<>("args", args));
    facts.add(new Fact<>("state", new EmptyState()));
    return facts;
  }

  public static Facts formBasicsFacts(String command, MyteamEvent event) {
    Facts facts = new Facts();
    facts.put("command", command);
    facts.add(new Fact<>("event", event));
    facts.add(new Fact<>("isGroup", event.getChatType().equals(ChatType.GROUP)));
    return facts;
  }

  public static Facts formBasicsFacts(RuleType command, MyteamEvent event) {
    return formBasicsFacts(command.getName(), event);
  }

  public static Facts formStateActionFacts(BotState state, String args, MyteamEvent event) {
    Facts facts = new Facts();
    facts.put("args", args);
    facts.add(new Fact<>("state", state));
    facts.add(new Fact<>("event", event));
    facts.add(new Fact<>("isGroup", event.getChatType().equals(ChatType.GROUP)));
    facts.put("command", "");
    return facts;
  }
}
