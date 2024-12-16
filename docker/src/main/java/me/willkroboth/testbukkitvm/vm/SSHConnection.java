package me.willkroboth.testbukkitvm.vm;

import com.jcraft.jsch.*;
import me.willkroboth.testbukkitvm.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

public class SSHConnection implements AutoCloseable {
    private final Session session;

    public SSHConnection(String vmIP) throws JSchException {
        System.out.println("Connecting to " + vmIP);
        session = new JSch().getSession("root", vmIP);
        // This is not a secure password, but it's not secure anyway since it is publicly accessible right here.
        //  I think any other authentication method would also be public right here anyway.
        //  I *believe* that this isn't a big deal, because even though the vm can access the internet through
        //  the virtual bridge, it can only accept ssh from the host machine (or at least I want it to be like that).
        //  https://wiki.libvirt.org/VirtualNetworking.html#network-address-translation-nat
        // Also, not setting a password on the VM doesn't seem to work ¯\_(ツ)_/¯
        session.setPassword("sshPassword");

        // Turning off host key checking is apparently not recommended in production https://stackoverflow.com/a/2003460
        //  I'm not entirely sure if that is a problem here?
        //  Configuring to allow the host seems tricky since the VM's key seems to be different each time its created
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect();
    }

    @Override
    public void close() {
        System.out.println("Disconnecting from " + session.getHostKey().getHost());
        session.disconnect();
    }

    public void uploadResources(Map<String, InputStream> resources, String remoteDestination) throws JSchException, SftpException {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();

        System.out.println("Sending files to " + remoteDestination);
        for (Map.Entry<String, InputStream> resource : resources.entrySet()) {
            System.out.println("Uploading " + resource.getKey());
            sftp.put(resource.getValue(), Path.of(remoteDestination, resource.getKey()).toString());
        }
        System.out.println("Done!");

        sftp.disconnect();
    }

    public void uploadFile(File localFile, Path remoteDestination) throws JSchException, SftpException, IOException {
        ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();

        System.out.println("Sending files to " + remoteDestination);

        Resource.writeDirectory(localFile, remoteDestination, (file, destination) -> {
            try (FileInputStream input = new FileInputStream(file)) {
                sftp.put(input, destination.toString());
            }
        });

        System.out.println("Done!");

        sftp.disconnect();
    }

    public String executeCommand(String command) throws JSchException, IOException {
        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(command);

        InputStream in = exec.getInputStream();
        exec.connect();

        StringBuilder output = new StringBuilder();
        int readByte;
        while ((readByte = in.read()) != -1) {
            output.append((char) readByte);
        }

        exec.disconnect();
        return output.toString();
    }

    public void executeCommandNoWait(String command) throws JSchException {
        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(command);

        // Just connect and disconnect
        exec.connect();
        exec.disconnect();
    }
}
