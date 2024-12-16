package me.willkroboth.testbukkitvm.vm.guestagent;

import com.google.gson.JsonObject;

public interface CommandProperty {
    void addToArguments(JsonObject arguments, String property);
}
