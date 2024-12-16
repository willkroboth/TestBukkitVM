package me.willkroboth.testbukkitvm;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    // Note: users do need to install docker engine
    //  https://docker-curriculum.com/#:~:text=Docker%20Hub-,Setting%20up%20your%20computer,-Getting%20all%20the
    //  Does seem easier than them figuring out how to install and setup libvirt, so still a good idea to containerize.
    //  It also seems like VMs created on one system don't work on another, so creating a consistent creation/run
    //  environment with a container seems to help make this portable.

    public static void main(String[] args) throws IOException {
        String filesPath = args[0];
        File projectDirectory = new File(filesPath);

        // Connect to Docker
        DockerClient client = createDockerClient();

        System.out.println(client.listContainersCmd().exec());

        // Load docker image
        File dockerImage = new File(projectDirectory, "dockerRunVm.tar");
        client.loadImageCmd(new FileInputStream(dockerImage)).exec();

        client.close();
    }

    private static DockerClient createDockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();

        return DockerClientBuilder.getInstance(config)
            .withDockerHttpClient(httpClient)
            .build();
    }
}
