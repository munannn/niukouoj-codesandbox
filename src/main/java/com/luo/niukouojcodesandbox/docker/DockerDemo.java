package com.luo.niukouojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;

import java.util.List;

/**
 * @author 木南
 * @version 1.0
 * @Description
 */
public class DockerDemo {
    public static void main(String[] args) {
        // 获取默认的 Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取镜像
        String image = "nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println("下载镜像：" + item.getStatus());
//                super.onNext(item);
//            }
//        };


//        pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
//        System.out.println("下载完成");
        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse = containerCmd.withCmd("echo", "hello,docker").exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();
        // 查看容器状态
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
        List<Container> containerList = listContainersCmd.withShowAll(true).exec();
        for (Container container : containerList) {
            System.out.println(container);
        }
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 删除容器
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        // 删除镜像
        //dockerClient.removeImageCmd(image).exec();
    }
}
