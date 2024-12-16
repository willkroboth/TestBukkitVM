package me.willkroboth.testbukkitvm.server;

import me.willkroboth.testbukkitvm.vm.Snapshot;
import me.willkroboth.testbukkitvm.vm.VMManager;
import org.libvirt.LibvirtException;

public class ServerManager {
    private final VMManager vmManager;

    public ServerManager(VMManager vmManager) {
        this.vmManager = vmManager;
    }

    public void rerunServer() throws LibvirtException {
        vmManager.restoreSnapshot(Snapshot.SERVER);
        vmManager.domain().resume();

        vmManager.waitForGuestAgent();
//        System.out.println(vmManager.executeCommand("/root/server/run.sh"));
    }
}
