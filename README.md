# RancherClient
A simple java rancher client for showing all pods information.

This library require JDK8 and above.

Usage example as below:

```
String url = ""; // rancher host url
String token = ""; // api token generated from Rancher

RancherClient client = new RancherClient(url, token);
client.setVerbose(false); // verbose internal information, default:true

JSONObject projects = client.getProjects("?name=my_project_name"); // name is filter parameter of rancher api

List<String> nameSpaces = new ArrayList<>();
nameSpaces.add("?namespaceId=namespace_1");
nameSpaces.add("?namespaceId=namespace_2");

List<RancherPod> list = client.getRancherPods(projects, nameSpaces);

for (RancherPod pod : list) {
    if (pod.nameSpaceId.contains("namespace_1")) {
        System.out.println(pod);
    }
}

```
