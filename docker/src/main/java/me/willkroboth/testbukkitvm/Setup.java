package me.willkroboth.testbukkitvm;

import me.willkroboth.testbukkitvm.vm.Snapshot;
import me.willkroboth.testbukkitvm.vm.VMCreator;
import me.willkroboth.testbukkitvm.vm.VMExporter;
import me.willkroboth.testbukkitvm.vm.VMManager;
import org.libvirt.Connect;
import org.libvirt.Domain;

import java.io.File;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class Setup {

    public static void main(String[] args) throws Exception {
        String vmName = args[0];
        String filesPath = args[1];
        File projectDirectory = new File(filesPath);

        // Connect to qemu
        Connect connect = new Connect("qemu:///system");

        // Connect to machine
        Domain domain = connect.domainLookupByName(vmName);

        // Reset to standard point
        VMManager manager = new VMManager(domain, null);
        manager.restoreSnapshot(Snapshot.SETUP);

        domain.resume();

        // Add dependencies and files that all machines need
        manager.writeFile(new File(projectDirectory, "vmFiles"), Path.of("root", "vmFiles"));

        // Allow executing scripts
        manager.executeCommand("chmod +x /root/vmFiles/installPackages.sh");
        manager.executeCommand("chmod +x /root/vmFiles/resetNetworking.sh");

        // Wait for packages to complete
        System.out.println("Installing packages...");
        System.out.println(manager.executeCommand("/root/vmFiles/installPackages.sh"));
        System.out.println("Done!");

        // Create snapshot
        domain.suspend();
        manager.createSnapshot(Snapshot.BASE);

        // Export VM
        File vmZip = new File(projectDirectory, vmName + ".zip");
        new VMExporter(domain)
            .exportVM(vmZip);

        VMCreator.setupFromZip(
            connect,
            new File(projectDirectory, "storage"),
            new ZipFile(vmZip)
        );

        connect.close();
    }
}
