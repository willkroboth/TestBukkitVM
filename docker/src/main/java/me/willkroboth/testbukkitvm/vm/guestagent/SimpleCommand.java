package me.willkroboth.testbukkitvm.vm.guestagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

public class SimpleCommand<T> implements GuestAgentCommand<T> {
    // Instance definition
    public static final ReturnParser<Void> EMPTY = returnElement -> null;

    @FunctionalInterface
    public interface ReturnParser<T> {
        T extractReturnResult(JsonElement returnElement);
    }

    private final String name;
    private final ReturnParser<T> returnParser;

    private final JsonObject arguments;
    private boolean argumentsRequired = true;

    public SimpleCommand(String name, ReturnParser<T> returnParser) {
        this.name = name;
        this.returnParser = returnParser;

        this.arguments = new JsonObject();
    }

    // https://www.qemu.org/docs/master/interop/qmp-spec.html#issuing-commands
    public T run(Domain domain, boolean log) throws LibvirtException {
        JsonObject command = new JsonObject();
        command.addProperty("execute", this.name);
        command.add("arguments", this.arguments);

        String prompt = command.toString();
        if (log) System.out.println("Sending guest agent command to " + domain.getName() + ": " + prompt);

        // https://libvirt.org/html/libvirt-libvirt-qemu.html#virDomainQemuAgentCommand
        String response = domain.qemuAgentCommand(prompt, -1 /* Default timeout */, 0);
        if (log) System.out.println("Received " + response);

        JsonElement returnElement = JsonParser.parseString(response).getAsJsonObject().get("return");
        return this.returnParser.extractReturnResult(returnElement);
    }

    // Build args
    public SimpleCommand<T> argumentsNowOptional() {
        this.argumentsRequired = false;

        return this;
    }

    public boolean propertyGiven(String property, Object value) {
        if (value != null) {
            return true;
        } else if (argumentsRequired) {
            throw new IllegalArgumentException("Property " + property + " must be given, but was null");
        }
        return false;
    }

    public SimpleCommand<T> addProperty(String property, String value) {
        if (propertyGiven(property, value)) {
            this.arguments.addProperty(property, value);
        }

        return this;
    }

    public SimpleCommand<T> addProperty(String property, Number value) {
        if (propertyGiven(property, value)) {
            this.arguments.addProperty(property, value);
        }

        return this;
    }

    public SimpleCommand<T> addProperty(String property, CommandProperty value) {
        if (propertyGiven(property, value)) {
            value.addToArguments(this.arguments, property);
        }

        return this;
    }

    public SimpleCommand<T> addPropertyIfNotDefault(String property, boolean value, boolean defaultValue) {
        if (value != defaultValue) this.arguments.addProperty(property, value);

        return this;
    }

    public SimpleCommand<T> addArray(String property, String[] elements) {
        if (propertyGiven(property, elements) && elements.length != 0) {
            JsonArray array = new JsonArray();
            for (String element : elements) {
                array.add(element);
            }

            this.arguments.add(property, array);
        }

        return this;
    }
}
