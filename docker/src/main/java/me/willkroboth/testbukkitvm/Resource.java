package me.willkroboth.testbukkitvm;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Resource {
    private Resource() {

    }

    public static final String CONFIG = "config.xml";
    public static final String SNAPSHOTS = "snapshots.xml";
    public static final String IMAGE = "image.qcow2";
    public static final String OS = "os.iso";


    public static void addToZip(Map<String, InputStream> resources, ZipOutputStream outputStream) throws IOException {
        for (Map.Entry<String, InputStream> resource : resources.entrySet()) {
            System.out.println("Zipping " + resource.getKey());
            outputStream.putNextEntry(new ZipEntry(resource.getKey()));
            IOUtils.copy(resource.getValue(), outputStream);
            outputStream.closeEntry();
        }
    }

    public static Map<String, InputStream> readFromZip(ZipFile zipFile) throws IOException {
        Map<String, InputStream> resources = new HashMap<>();

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            String name = entry.getName();
            InputStream itemStream = zipFile.getInputStream(entry);

            resources.put(name, itemStream);
        }

        return resources;
    }

    public static <E extends Exception> void writeDirectory(File localFile, Path remoteDestination, ThrowableBiConsumer<File, Path, E> fileWriter) throws IOException, E {
        if (!localFile.isDirectory()) {
            fileWriter.accept(localFile, remoteDestination);
            return;
        }

        File[] subFiles = localFile.listFiles();
        if (subFiles == null) {
            throw new IOException("Could not read directory: " + localFile);
        }
        for (File subFile : subFiles) {
            writeDirectory(subFile, remoteDestination.resolve(subFile.getName()), fileWriter);
        }
    }

    @FunctionalInterface
    public interface ThrowableBiConsumer<A, B, E extends Exception> {
        void accept(A a, B b) throws E, IOException;
    }
}
