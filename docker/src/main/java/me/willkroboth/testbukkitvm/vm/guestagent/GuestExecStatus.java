package me.willkroboth.testbukkitvm.vm.guestagent;

// https://qemu-project.gitlab.io/qemu/interop/qemu-ga-ref.html#qapidoc-221
public record GuestExecStatus(boolean exited, String outData, String errData) {
}
