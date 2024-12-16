package me.willkroboth.testbukkitvm.vm;

import me.willkroboth.testbukkitvm.Resource;
import org.apache.commons.io.IOUtils;
import org.libvirt.Domain;
import org.libvirt.DomainSnapshot;
import org.libvirt.LibvirtException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class VMExporter {
    private final Domain domain;

    public VMExporter(Domain domain) {
        this.domain = domain;
    }

    public void exportVM(File exportFile) throws LibvirtException, IOException, ParserConfigurationException, SAXException, TransformerException {
        Map<String, InputStream> resources = new HashMap<>();

        // Export the VM XML configuration
        String xmlString = domain.getXMLDesc(0);

        // Parse the XML for reading and editing
        Element xmlConfig = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(xmlString)))
            .getDocumentElement();

        String[] removedValues = sanitizeDomainInformation(xmlConfig, false);

        // Export the VM file
        String vmPath = removedValues[0];
        resources.put(Resource.IMAGE, Files.newInputStream(Path.of(vmPath)));

        // Export the OS image
        String osPath = removedValues[1];
        resources.put(Resource.OS, Files.newInputStream(Path.of(osPath)));

        // Export the XML config
        Transformer xmlWriter = TransformerFactory.newInstance().newTransformer();
        xmlWriter.setOutputProperty(OutputKeys.INDENT, "yes");
        xmlWriter.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        xmlWriter.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter xmlOutput = new StringWriter();
        xmlWriter.transform(new DOMSource(xmlConfig), new StreamResult(xmlOutput));

        resources.put(Resource.CONFIG, IOUtils.toInputStream(xmlOutput.toString(), Charset.defaultCharset()));

        // Export snapshots
        Document snapshotXMLCollection = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument();
        Element snapshotsRoot = snapshotXMLCollection.createElement("snapshots");
        snapshotXMLCollection.appendChild(snapshotsRoot);

        for (String snapshotName : domain.snapshotListNames(Domain.SnapshotListFlags.TOPOLOGICAL)) {
            // Lookup each snapshot
            DomainSnapshot snapshot = domain.snapshotLookupByName(snapshotName);
            String snapshotXMLString = snapshot.getXMLDesc();

            // Convert xml into node for editing
            Element snapshotElement = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader(snapshotXMLString)))
                .getDocumentElement();

            // Remove identifying domain information
            Element domainElement = (Element) snapshotElement.getElementsByTagName("domain").item(0);
            sanitizeDomainInformation(domainElement, true);

            Element inactiveDomain = (Element) snapshotElement.getElementsByTagName("inactiveDomain").item(0);
            sanitizeDomainInformation(inactiveDomain, true);

            // Add to collection
            snapshotsRoot.appendChild(snapshotXMLCollection.importNode(snapshotElement, true));
        }

        StringWriter snapshotXmlOutput = new StringWriter();
        xmlWriter.transform(new DOMSource(snapshotXMLCollection), new StreamResult(snapshotXmlOutput));

        resources.put(Resource.SNAPSHOTS, IOUtils.toInputStream(snapshotXmlOutput.toString(), Charset.defaultCharset()));

        // Zip all resources
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(exportFile));
        Resource.addToZip(resources, out);
        out.close();
        System.out.println("Done!");
    }

    private String[] sanitizeDomainInformation(Element domainElement, boolean snapshot) {
        // Caller may need to know location of image and os
        String[] removedValues = new String[2];

        // Remove identifying options from config so they can be generated for new machines
        domainElement.removeAttribute("id");

        Node uuidNode = domainElement.getElementsByTagName("uuid").item(0);
        if (snapshot) {
            // Make sure UUID information matches in snapshot
            uuidNode.setTextContent(VMCreator.XML.UUID);
        } else {
            // UUID will be defined to appropriate value on creation
            domainElement.removeChild(uuidNode);
        }

        // Insert placeholder strings so they can be substituted with new values later
        Node nameNode = domainElement.getElementsByTagName("name").item(0);
        nameNode.setTextContent(VMCreator.XML.NAME);

        Element devices = (Element) domainElement.getElementsByTagName("devices").item(0);

        // Find network interface
        Element networkElement = (Element) devices.getElementsByTagName("interface").item(0);
        Element macElement = (Element) networkElement.getElementsByTagName("mac").item(0);
        if (snapshot) {
            // Make sure mac address matches in snapshot
            macElement.setAttribute("address", VMCreator.XML.MAC_ADDRESS);
        } else {
            // Mac address will be defined to appropriate value on creation
            networkElement.removeChild(macElement);
        }

        // Find disks
        NodeList disks = devices.getElementsByTagName("disk");
        Element vmFile = (Element) disks.item(0);
        Element osImage = (Element) disks.item(1);

        // Get vm image
        Element vmSource = (Element) vmFile.getElementsByTagName("source").item(0);
        removedValues[0] = vmSource.getAttribute("file");
        vmSource.setAttribute("file", VMCreator.XML.IMAGE);

        // Get vm os
        Element osSource = (Element) osImage.getElementsByTagName("source").item(0);
        if (osSource != null) {
            // For some reason, the <inactiveDomain> in a snapshot does not include specifically this field.
            removedValues[1] = osSource.getAttribute("file");
            osSource.setAttribute("file", VMCreator.XML.OS);
        }

        return removedValues;
    }
}