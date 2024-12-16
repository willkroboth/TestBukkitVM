package me.willkroboth.testbukkitvm.vm.guestagent;

import com.google.gson.JsonObject;

public enum FileOpenMode implements CommandProperty {
    // Same open modes as c's fopen: https://www.geeksforgeeks.org/c-fopen-function-with-examples/
    READ("rb"),
    WRITE("wb");

    private final String character;

    FileOpenMode(String character) {
        this.character = character;
    }

    @Override
    public void addToArguments(JsonObject arguments, String property) {
        arguments.addProperty(property, character);
    }
}
