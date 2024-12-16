package me.willkroboth.testbukkitvm.server;

import me.willkroboth.testbukkitvm.vm.Snapshot;
import me.willkroboth.testbukkitvm.vm.VMCreator;
import me.willkroboth.testbukkitvm.vm.VMManager;
import org.libvirt.LibvirtException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

public class ServerCreator {
    private static final String BASE_STORAGE = "serverTemplate";
    // TODO: Probably want to cache server versions on host machine using the main program
    //  Actually, vmImages should probably also be cached on host machine since they should be reusable
    //  Is it possible to have the Docker container access external files?
    //  See: Docker volumes?
    private static final String VERSIONS_STORAGE = "serverVersions";

    private final VMCreator vmCreator;

    private final File serverTemplate;
    private final File serverVersions;

    public ServerCreator(VMCreator vmCreator, File storageDirectory) {
        this.vmCreator = vmCreator;

        this.serverTemplate = new File(storageDirectory, BASE_STORAGE);
        this.serverVersions = new File(storageDirectory, VERSIONS_STORAGE);
    }

    public ServerManager createServer(String version) throws LibvirtException, IOException, ParserConfigurationException, SAXException, TransformerException {
        // Lookup version
        File serverJar = new File(serverVersions, version + ".jar");
        if (!serverJar.exists()) {
            throw new FileNotFoundException("Could not find server version " + version + " (" + serverJar + ")");
        }

        // Create VM
        VMManager vmManager = vmCreator.createVM(version);

        // Initialize VM
        vmManager.restoreSnapshot(Snapshot.BASE);
        vmManager.domain().resume();
        vmManager.waitForGuestAgent();

        int resetNetworkingPID = vmManager.executeCommandAsync("/root/vmFiles/resetNetworking.sh", false);

        // Upload server to vm
        Path serverPath = Path.of("root", "server");
        vmManager.writeFile(serverTemplate, serverPath);
        // Transferring the server jar takes a while...
        vmManager.writeFile(serverJar, serverPath.resolve("server.jar"));

        // Allow executing scripts
        vmManager.executeCommand("chmod +x /root/server/run.sh");

        // Wait for networking update to complete
        //  (if it's not done already, probably faster than the jar upload, which is why we did it async :) )
        System.out.println("Waiting for network to connect...");
        vmManager.waitForProcessFinish(resetNetworkingPID);
        System.out.println("Done!");

        // Create snapshot
        vmManager.domain().suspend();
        vmManager.createSnapshot(Snapshot.SERVER);

        // Create manager
        return new ServerManager(vmManager);
    }
}
