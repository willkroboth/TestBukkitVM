package me.willkroboth.testbukkitvm.vm;

public enum Snapshot {
    LOGIN("Login", "Logged into machine"),
    SETUP("Setup", "Finished OS configuration"),
    BASE("Base", "Added files and dependencies needed for all machines"),
    SERVER("Server", "Created files for Minecraft server");

    private final String name;
    private final String description;

    Snapshot(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
