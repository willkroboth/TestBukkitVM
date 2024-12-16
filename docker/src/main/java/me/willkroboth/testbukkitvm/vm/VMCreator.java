package me.willkroboth.testbukkitvm.vm;

import me.willkroboth.testbukkitvm.Resource;
import org.apache.commons.io.IOUtils;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;

public class VMCreator {
    private static final String BASE_STORAGE = "vmData";
    private static final String VM_STORAGE = "vms";

    public static class XML {
        public static final String NAME = "{VM_NAME}";
        public static final String IMAGE = "{VM_IMAGE}";
        public static final String OS = "{VM_OS}";

        public static final String UUID = "{VM_UUID}";
        public static final String MAC_ADDRESS = "{VM_MAC_ADDRESS}";
    }

    private final Connect connect;

    private final File vmDirectory;

    private final String baseXML;
    private final String[] snapshotXMLs;
    private final Path baseImage;
    private final String baseOSLocation;

    public static VMCreator setupFromZip(Connect connect, File storageDirectory, ZipFile resourcesZip) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        // Unzip resources
        Map<String, InputStream> resources = Resource.readFromZip(resourcesZip);

        InputStream baseXML = resources.get(Resource.CONFIG);
        InputStream snapshotXML = resources.get(Resource.SNAPSHOTS);
        InputStream baseImage = resources.get(Resource.IMAGE);
        InputStream baseOS = resources.get(Resource.OS);

        // Setup base storage directory
        Path baseStorage = storageDirectory.toPath().resolve(BASE_STORAGE);
        File baseStorageFile = baseStorage.toFile();
        if (!baseStorageFile.isDirectory() && !baseStorageFile.mkdirs()) {
            throw new NoSuchFileException("Could not create storage directory <" + baseStorage + ">");
        }

        // Setup vm storage directory
        File vmStorage = new File(storageDirectory, VM_STORAGE);
        if (!vmStorage.isDirectory() && !vmStorage.mkdirs()) {
            throw new NoSuchFileException("Could not create storage directory <" + vmStorage + ">");
        }

        // Unzip resources
        System.out.println("Unpacking config.xml");
        IOUtils.copy(baseXML, Files.newOutputStream(baseStorage.resolve(Resource.CONFIG)));
        System.out.println("Unpacking snapshots.xml");
        IOUtils.copy(snapshotXML, Files.newOutputStream(baseStorage.resolve(Resource.SNAPSHOTS)));
        System.out.println("Unpacking image.qcow2");
        IOUtils.copy(baseImage, Files.newOutputStream(baseStorage.resolve(Resource.IMAGE)));
        System.out.println("Unpacking os.iso");
        IOUtils.copy(baseOS, Files.newOutputStream(baseStorage.resolve(Resource.OS)));
        System.out.println("Done!");

        return new VMCreator(connect, storageDirectory);
    }

    public VMCreator(Connect connect, File storageDirectory) throws IOException, ParserConfigurationException, SAXException, TransformerException {
        this.connect = connect;

        this.vmDirectory = new File(storageDirectory, VM_STORAGE);

        // Read base files
        Path baseStorage = storageDirectory.toPath().resolve(BASE_STORAGE);

        this.baseXML = Files.readString(baseStorage.resolve(Resource.CONFIG));
        this.baseImage = baseStorage.resolve(Resource.IMAGE);
        this.baseOSLocation = baseStorage.resolve(Resource.OS).toString();

        // Extract each snapshot entry from collection
        String rawSnapshotXML = Files.readString(baseStorage.resolve(Resource.SNAPSHOTS));
        Element snapshotsRoot = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(rawSnapshotXML)))
            .getDocumentElement();
        NodeList snapshots = snapshotsRoot.getElementsByTagName("domainsnapshot");

        this.snapshotXMLs = new String[snapshots.getLength()];
        for (int i = 0; i < snapshots.getLength(); i++) {
            Node snapshot = snapshots.item(i);

            // Convert snapshot back to string
            StringWriter xmlResult = new StringWriter();
            TransformerFactory.newInstance().newTransformer().transform(new DOMSource(snapshot), new StreamResult(xmlResult));

            this.snapshotXMLs[i] = xmlResult.toString();
        }
    }

    public VMManager createVM(String vmName) throws IOException, LibvirtException, ParserConfigurationException, SAXException {
        // Create copy of image
        File vmImage = new File(vmDirectory, vmName + ".qcow2");
        System.out.println("Creating image for " + vmName);
        Files.copy(baseImage, vmImage.toPath());

        // Configure xml
        String vmXML = baseXML
            .replace(XML.NAME, vmName)
            .replace(XML.IMAGE, vmImage.getPath())
            .replace(XML.OS, baseOSLocation);

        // Load xml
        System.out.println("Defining domain for " + vmName);
        Domain domain = connect.domainDefineXML(vmXML);

        // Read domain xml to extract generated values
        Element generatedConfig = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(domain.getXMLDesc(0))))
            .getDocumentElement();

        Node uuidNode = generatedConfig.getElementsByTagName("uuid").item(0);
        String uuid = uuidNode.getTextContent();

        Element devicesElement = (Element) generatedConfig.getElementsByTagName("devices").item(0);
        Element networkElement = (Element) devicesElement.getElementsByTagName("interface").item(0);
        Element macElement = (Element) networkElement.getElementsByTagName("mac").item(0);
        String macAddress = macElement.getAttribute("address");

        // Load snapshots - https://stackoverflow.com/a/45578064
        for (String snapshot : snapshotXMLs) {
            String snapShotXML = snapshot
                .replace(XML.NAME, vmName)
                .replace(XML.IMAGE, vmImage.getPath())
                .replace(XML.OS, baseOSLocation)
                // Make sure snapshots have same configuration as main machine
                .replace(XML.UUID, uuid)
                .replace(XML.MAC_ADDRESS, macAddress);

            domain.snapshotCreateXML(snapShotXML, Domain.SnapshotCreateFlags.REDEFINE);
        }

        System.out.println("Done!");
        return new VMManager(domain, vmImage);
    }

    public VMManager lookUpVM(String vmName) throws FileNotFoundException, LibvirtException {
        // Find image file
        File vmImage = new File(vmDirectory, vmName + ".qcow2");

        if (!vmImage.exists()) {
            throw new FileNotFoundException("Could not find VM image " + vmImage.getPath());
        }

        // Lookup vm
        Domain domain = connect.domainLookupByName(vmName);

        return new VMManager(domain, vmImage);
    }
}
