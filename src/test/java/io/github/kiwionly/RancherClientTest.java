package io.github.kiwionly;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static io.github.kiwionly.RancherClient.RancherPod;

/**
 *  Example of usage
 *
 */
public class RancherClientTest {

    public static void main(String[] args) throws Exception {

        long start = System.currentTimeMillis();

        String url = ""; // rancher host url
        String token = ""; // api token generated from Rancher

        RancherClient client = new RancherClient(url, token);
        client.setVerbose(false); // verbose internal information, default:true

        JSONObject projects = client.getProjects("?name=my_project_name"); // name is filter parameter of rancher api

        List<String> nameSpaces = new ArrayList<>();
        nameSpaces.add("?namespaceId=namespace_1");
        nameSpaces.add("?namespaceId=namespace_2");

        List<RancherPod> list = client.getRancherPods(projects, nameSpaces);

        client.printHeader();

        System.out.println();
        System.out.println("--- namespace_1");
        System.out.println();

        for (RancherPod pod : list) {
            if (pod.nameSpaceId.contains("namespace_1")) {
                System.out.println(pod);
            }
        }

        System.out.println();
        System.out.println(System.currentTimeMillis() - start + "ms");

    }

}