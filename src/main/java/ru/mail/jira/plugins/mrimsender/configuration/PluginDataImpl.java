package ru.mail.jira.plugins.mrimsender.configuration;

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import java.util.List;

public class PluginDataImpl implements PluginData {
    private static final String PLUGIN_PREFIX = "ru.mail.jira.plugins.mrimsender:";
    private static final String TOKEN = PLUGIN_PREFIX + "token";
    private static final String ENABLED_BY_DEFAULT = PLUGIN_PREFIX + "enabledByDefault";
    private static final String NOTIFIED_USER_KEYS = PLUGIN_PREFIX + "notifiedUserKeys";
    private static final String MAIN_NODE_ID = PLUGIN_PREFIX + "mainNodeId";
    private static final String BOT_API_URL = PLUGIN_PREFIX + "botApiUrl";
    private static final String BOT_NAME = PLUGIN_PREFIX + "botName";
    private static final String BOT_LINK = PLUGIN_PREFIX + "botLink";

    private final PluginSettingsFactory pluginSettingsFactory;

    public PluginDataImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String getToken() {
        return (String) pluginSettingsFactory.createGlobalSettings().get(TOKEN);
    }

    @Override
    public void setToken(String token) {
        pluginSettingsFactory.createGlobalSettings().put(TOKEN, token);
    }

    @Override
    public boolean isEnabledByDefault() {
        return Boolean.parseBoolean((String) pluginSettingsFactory.createGlobalSettings().get(ENABLED_BY_DEFAULT));
    }

    @Override
    public void setEnabledByDefault(boolean enabledByDefault) {
        pluginSettingsFactory.createGlobalSettings().put(ENABLED_BY_DEFAULT, String.valueOf(enabledByDefault));
    }

    @Override
    public List<String> getNotifiedUserKeys() {
        //noinspection unchecked
        return (List<String>) pluginSettingsFactory.createGlobalSettings().get(NOTIFIED_USER_KEYS);
    }

    @Override
    public void setNotifiedUserKeys(List<String> notifiedUserKeys) {
        pluginSettingsFactory.createGlobalSettings().put(NOTIFIED_USER_KEYS, notifiedUserKeys);
    }

    @Override
    public String getMainNodeId() {
        return (String)pluginSettingsFactory.createGlobalSettings().get(MAIN_NODE_ID);
    }

    @Override
    public void setMainNodeId(String mainNodeId) {
        pluginSettingsFactory.createGlobalSettings().put(MAIN_NODE_ID, mainNodeId);
    }

    @Override
    public String getBotApiUrl() {
        return (String) pluginSettingsFactory.createGlobalSettings().get(BOT_API_URL);
    }

    @Override
    public void setBotApiUrl(String botApiUrl) {
        pluginSettingsFactory.createGlobalSettings().put(BOT_API_URL, botApiUrl);
    }

    @Override
    public String getBotName() {
        return (String) pluginSettingsFactory.createGlobalSettings().get(BOT_NAME);
    }

    @Override
    public void setBotName(String botName) {
        pluginSettingsFactory.createGlobalSettings().put(BOT_NAME, botName);
    }

    @Override
    public String getBotLink() {
        return (String) pluginSettingsFactory.createGlobalSettings().get(BOT_LINK);
    }

    @Override
    public void setBotLink(String botLink) {
        pluginSettingsFactory.createGlobalSettings().put(BOT_LINK, botLink);
    }
}
