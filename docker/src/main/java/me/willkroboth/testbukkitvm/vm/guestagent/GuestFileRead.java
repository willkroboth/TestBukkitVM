package me.willkroboth.testbukkitvm.vm.guestagent;

// https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#object-QGA-qapi-schema.GuestFileRead
public record GuestFileRead(int count, byte[] bytes, boolean eof) {
}
