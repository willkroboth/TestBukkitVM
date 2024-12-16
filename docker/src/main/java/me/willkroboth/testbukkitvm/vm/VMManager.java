package me.willkroboth.testbukkitvm.vm;

import com.jcraft.jsch.JSchException;
import me.willkroboth.testbukkitvm.Resource;
import me.willkroboth.testbukkitvm.vm.guestagent.FileOpenMode;
import me.willkroboth.testbukkitvm.vm.guestagent.GuestAgentCommand;
import me.willkroboth.testbukkitvm.vm.guestagent.GuestExecStatus;
import org.libvirt.Domain;
import org.libvirt.DomainInterface;
import org.libvirt.DomainSnapshot;
import org.libvirt.LibvirtException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class VMManager {
    private final Domain domain;
    private final String domainName;

    private final File imageFile;

    public VMManager(Domain domain, File imageFile) throws LibvirtException {
        this.domain = domain;
        this.domainName = domain.getName();

        this.imageFile = imageFile;
    }

    // Domain management
    public Domain domain() {
        return this.domain;
    }

    public void create() throws LibvirtException {
        domain.create();
    }

    public void destroy() throws LibvirtException, IOException {
        // Remove VM
        if (domain.isActive() == 1) {
            domain.destroy();
        }
        for (String snapshotName : domain.snapshotListNames()) {
            // 0 flags, just delete this snapshot
            domain.snapshotLookupByName(snapshotName).delete(0);
        }
        domain.undefine();

        // Remove image
        Files.delete(imageFile.toPath());
    }

    // Snapshots
    public DomainSnapshot createSnapshot(Snapshot snapshot) throws LibvirtException, ParserConfigurationException, TransformerException {
        return createSnapshot(snapshot.getName(), snapshot.getDescription());
    }

    public DomainSnapshot createSnapshot(String name, String description) throws LibvirtException, ParserConfigurationException, TransformerException {
        System.out.println("Creating snapshot for " + domainName + ": " + name);
        // Create xml to configure snapshot
        Document snapshotXML = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument();

        Element rootElement = snapshotXML.createElement("domainsnapshot");
        snapshotXML.appendChild(rootElement);

        // Set snapshot name
        Element nameElement = snapshotXML.createElement("name");
        rootElement.appendChild(nameElement);
        nameElement.setTextContent(name);

        // Set snapshot description
        Element descriptionElement = snapshotXML.createElement("description");
        rootElement.appendChild(descriptionElement);
        descriptionElement.setTextContent(description);

        // Write xml to string
        StringWriter xmlResult = new StringWriter();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(snapshotXML), new StreamResult(xmlResult));

        // Create snapshot
//        System.out.println(xmlResult);
        return domain.snapshotCreateXML(xmlResult.toString());
    }

    public void restoreSnapshot(Snapshot snapshot) throws LibvirtException {
        restoreSnapshot(domain.snapshotLookupByName(snapshot.getName()));
    }

    public void restoreSnapshot(DomainSnapshot snapshot) throws LibvirtException {
        String snapshotDescription = snapshot.getXMLDesc();
        int start = snapshotDescription.indexOf("<name>") + "<name>".length();
        int end = snapshotDescription.indexOf("</name>");
        String snapshotName = snapshotDescription.substring(start, end);

        System.out.println("Restoring " + domainName + " to snapshot " + snapshotName);
        domain.revertToSnapshot(snapshot);
        System.out.println("Done!");
    }

    public boolean isGuestAgentAvailable() throws LibvirtException {
        // Domain is not running
        if (domain.isActive() == 0) return false;

        // Check for the guest agent
        return GuestAgentCommand.ping().run(domain, false);
    }

    public void waitForGuestAgent() throws LibvirtException {
        System.out.println("Trying to connect to guest agent on " + domain.getName() + "...");
        boolean connected;
        do {
            // This will spam the guest agent until the task is complete (which is why `log` is false :P)
            //  Not sure if there is a better way
            connected = isGuestAgentAvailable();
        } while (!connected);
        System.out.println("Connected!");
    }

    // Shell commands: https://www.0xf8.org/2022/01/executing-arbitrary-commands-in-your-libvirt-qemu-virtual-machine-through-qemu-guest-agent/
    public GuestExecStatus executeCommand(String command) throws LibvirtException {
        return waitForProcessFinish(executeCommandAsync(command, true));
    }

    public GuestExecStatus executeCommand(String path, String[] args) throws LibvirtException {
        return waitForProcessFinish(executeCommandAsync(path, args, true));
    }

    /**
     * @param command       The shell command to run
     * @param captureOutput Whether to collect stdout and stderr for this process, which can be retrieved later using `guest-exec-status`.
     * @return The pid of the process handling the command
     */
    public int executeCommandAsync(String command, boolean captureOutput) throws LibvirtException {
        String[] strings = command.split(" ");

        String path = strings[0];

        String[] arguments = new String[strings.length - 1];
        System.arraycopy(strings, 1, arguments, 0, arguments.length);

        return executeCommandAsync(path, arguments, captureOutput);
    }

    public int executeCommandAsync(String path, String[] args, boolean captureOutput) throws LibvirtException {
        return GuestAgentCommand.executeCommand(path, args, null, null, captureOutput).run(domain, true);
    }

    public GuestExecStatus waitForProcessFinish(int pid) throws LibvirtException {
        GuestAgentCommand<GuestExecStatus> getStatus = GuestAgentCommand.getExecutionStatus(pid);

        GuestExecStatus status;
        do {
            // This will spam the guest agent until the task is complete (which is why `log` is false :P)
            //  Not sure if there is a better way
            status = getStatus.run(domain, false);
        } while (!status.exited());

        return status;
    }

    // File IO
    public void writeFile(File localFile, Path remoteDestination) throws IOException, LibvirtException {
        System.out.println("Sending files to " + domainName + " " + localFile + " -> " + remoteDestination);

        Resource.writeDirectory(localFile, remoteDestination, (file, destination) -> {
            // Ensure directory exists on remote
            //  Not sure if qemu guest agent supports this functionality directly?
            waitForProcessFinish(executeCommandAsync("mkdir -p " + destination.getParent(), false));

            try (FileInputStream input = new FileInputStream(file)) {
                int fileHandle = GuestAgentCommand
                    .openFile(destination, FileOpenMode.WRITE)
                    .run(domain, false);

                GuestAgentCommand.writeFile(fileHandle, input.readAllBytes()).run(domain, false);
                GuestAgentCommand.closeFile(fileHandle).run(domain, false);
            }
        });
    }

    public void writeResources(Map<String, InputStream> resources, String remoteDestination) throws LibvirtException, IOException {
        System.out.println("Sending resources to " + domainName + " " + remoteDestination);

        for (Map.Entry<String, InputStream> resource : resources.entrySet()) {
            String fileName = resource.getKey();
            try (InputStream file = resource.getValue()) {
                int fileHandle = GuestAgentCommand.openFile(Path.of(remoteDestination, fileName), FileOpenMode.WRITE).run(domain, true);
                GuestAgentCommand.writeFile(fileHandle, file.readAllBytes()).run(domain, false);
                GuestAgentCommand.closeFile(fileHandle).run(domain, false);
            }
        }

        System.out.println("Done!");
    }

    public Map<String, InputStream> readResources(Set<String> resourceNames, String remoteDestination) throws LibvirtException {
        System.out.println("Reading resources from " + domainName + " " + remoteDestination);

        Map<String, InputStream> resources = new HashMap<>();

        for (String fileName : resourceNames) {
            int fileHandle = GuestAgentCommand.openFile(Path.of(remoteDestination, fileName), FileOpenMode.READ).run(domain, true);
            byte[] bytes = GuestAgentCommand.readFile(fileHandle).run(domain, false);
            GuestAgentCommand.closeFile(fileHandle).run(domain, false);

            resources.put(fileName, new ByteArrayInputStream(bytes));
        }

        System.out.println("Done!");
        return resources;
    }

    // SSH connection (TODO: redundant given we can use the guest agent for files and commands?)
    public SSHConnection connect() throws JSchException, LibvirtException {
        String ipAddress = getIP().getHostAddress();

        System.out.println("Connecting to " + domainName + " address: " + ipAddress);
        return new SSHConnection(ipAddress);
    }

    public Inet4Address getIP() throws LibvirtException {
        // Only have address if running
        if (domain.isActive() != 1)
            throw new IllegalStateException("Could not get ip for " + domainName + " because the domain is not running");

        Collection<DomainInterface> interfaces = domain.interfaceAddresses(Domain.InterfaceAddressesSource.VIR_DOMAIN_INTERFACE_ADDRESSES_SRC_AGENT, 0);
        for (DomainInterface networkInterface : interfaces) {
            if (!networkInterface.name.equals("eth0")) continue;

            for (DomainInterface.InterfaceAddress address : networkInterface.addrs) {
                InetAddress inetAddress = address.address;
                if (inetAddress instanceof Inet4Address inet4Address) {
                    return inet4Address;
                }
            }
        }

        throw new IllegalStateException("Could not get ip for " + domainName + ". No suitable network interface found: " + interfaces);
    }
}
