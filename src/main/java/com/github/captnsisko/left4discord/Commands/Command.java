package com.github.captnsisko.left4discord.Commands;

import org.javacord.api.entity.message.Message;

public abstract class Command {
    protected final String keyword;
    protected final String description;
    protected final String usage;
    protected final boolean visibleInHelp;

    // For normal commands
    public Command(String keyword, String description, String usage) {
        this.keyword = keyword;
        this.description = description;
        this.usage = usage;
        this.visibleInHelp = true;
    }

    // For other chat listeners
    public Command() {
        this.keyword = null;
        this.description = null;
        this.usage = null;
        this.visibleInHelp = false;
    }

    public boolean executeIfCalled(Message msg) {
        if(isCalled(msg)) {
            execute(msg);
            return true;
        }
        return false;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usage;
    }

    public boolean getVisibleInHelp() {
        return visibleInHelp;
    }

    protected abstract boolean isCalled(Message msg);
    protected abstract void execute(Message msg);
}