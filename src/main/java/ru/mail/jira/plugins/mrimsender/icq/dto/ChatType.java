package ru.mail.jira.plugins.mrimsender.icq.dto;

import javax.annotation.Nullable;

public enum ChatType {
    PRIVATE("private"),
    GROUP("group"),
    CHANNEL("channel");

    private String apiValue;

    ChatType(final String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

    @Nullable
    public static ChatType fromApiValue(final String value) {
        for (ChatType type : values()) {
            if (type.apiValue.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
