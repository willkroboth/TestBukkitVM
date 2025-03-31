package me.willkroboth.testbukkitvm;

import me.willkroboth.testbukkitvm.server.ServerCreator;
import me.willkroboth.testbukkitvm.server.ServerManager;
import me.willkroboth.testbukkitvm.vm.SSHConnection;
import me.willkroboth.testbukkitvm.vm.VMCreator;
import me.willkroboth.testbukkitvm.vm.VMManager;
import org.libvirt.Connect;
import org.libvirt.DomainSnapshot;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Run {
    public static void main(String[] args) throws Exception {
        String filesPath = args[0];
        File projectDirectory = new File(filesPath);

        // Connect to qemu
        Connect connect = new Connect("qemu:///system");
        // Set the error callback to nop https://libvirt.org/errors.html
        //  The default callback automatically logs the error to the console, which we don't want to do
        connect.setConnectionErrorCallback((userData, error) -> {
        });

        // Load server information
        File storageDirectory = new File(projectDirectory, "storage");
        VMCreator vmCreator = new VMCreator(connect, storageDirectory);
        ServerCreator serverCreator = new ServerCreator(vmCreator, storageDirectory);

        // Create server
        ServerManager serverManager = serverCreator.createServer("paper-1.21.4");
        serverManager.rerunServer();

        connect.close();
    }

    private static void testVMManager(VMCreator creator, File projectDirectory) throws Exception {
        // Create VMs
        VMManager testBukkitVM2 = creator.createVM("TestBukkitVM2");
        VMManager testBukkitVM3 = creator.createVM("TestBukkitVM3");

        // Test snapshot restore
//        VMManager manager = testBukkitVM2;
        for (VMManager manager : new VMManager[]{testBukkitVM2, testBukkitVM3}) {
            DomainSnapshot setupSnapshot = manager.domain().snapshotLookupByName("Base");
            manager.restoreSnapshot(setupSnapshot);

            // Start domain and ensure its guest agent is available
            manager.domain().resume();
            manager.waitForGuestAgent();

            // Make the new VM detect its NIC MAC address and get an ip address
            //  Using execute command to wait until it is fully done
            manager.executeCommand("/root/vmFiles/resetNetworking.sh");
        }

        File testFile = new File(projectDirectory, "/testFile");

        // Test guest-agent command and upload
        for (VMManager manager : new VMManager[]{testBukkitVM2, testBukkitVM3}) {
            manager.writeResources(Map.of("testFile", new FileInputStream(testFile)), "root");

            Map<String, InputStream> retrieve = manager.readResources(Set.of("testFile"), "root");

            InputStream retrievedTestFile = retrieve.get("testFile");
            String result = new BufferedReader(new InputStreamReader(retrievedTestFile)).lines().collect(Collectors.joining("\n"));
            System.out.println("Read file: <" + result + ">");
        }

        // Test ssh command and upload
        for (VMManager manager : new VMManager[]{testBukkitVM2, testBukkitVM3}) {
            try (SSHConnection connection = manager.connect()) {

                System.out.println(connection.executeCommand("echo test command return"));
                connection.executeCommand("mkdir command");
                connection.uploadResources(
                    Map.of("testFile", new FileInputStream(testFile)),
                    "/root/command"
                );
            }

//            manager.domain().suspend();
        }
    }
}
