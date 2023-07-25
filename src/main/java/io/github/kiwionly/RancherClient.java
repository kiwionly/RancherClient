package io.github.kiwionly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Simple Rancher client that reading all pods minimize information and status.
 *
 *
 */
public class RancherClient {

	private boolean verbose = true;

	private final OkHttpClient client;
	private final String url;
	private final String token;

	public RancherClient(String url, String token) {

		this.url = url;
		this.token = token;

		this.client = createUnsafeOkHttpClient();
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	private JSONObject get(String url) throws IOException {

		Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token).build();

		Response response = client.newCall(request).execute();

		String text = response.body().string();

		JSONObject data = JSON.parseObject(text);

		return data;
	}

	private OkHttpClient createUnsafeOkHttpClient() {

		try {

			// Create a trust manager that does not validate certificate chains
			final TrustManager[] trustAllCerts = new TrustManager[] {

					new X509TrustManager() {
						@Override
						public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
						}

						@Override
						public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
						}

						@Override
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return new java.security.cert.X509Certificate[] {};
						}
					} };

			// Install the trust manager
			final SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

			// Create an OkHttpClient that trusts all certificates
			final OkHttpClient.Builder builder = new OkHttpClient.Builder();
			builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]);
			builder.hostnameVerifier((hostname, session) -> true);

			return builder.build();

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public JSONObject getProjects(String name) throws IOException {

		JSONObject root = get(url);

		String type = root.getString("type");

		if(type.equals("error")) {
			throw new IllegalStateException(root.getString("message") + ", please check if your token is valid.");
		}

		JSONObject links = root.getJSONObject("links");
		String url = links.getString("projects") + name;

		JSONObject projects = get(url);

		return projects;
	}

	public List<String> getLink(JSONObject projects, String type, String nameSpace) {

		List<String> urlList = new ArrayList<>();

		JSONArray data = projects.getJSONArray("data");

		for (Object object : data) {

			JSONObject obj = (JSONObject) object;
			JSONObject links = obj.getJSONObject("links");
			String url = links.getString(type) + nameSpace;

			urlList.add(url);
		}

		return urlList;
	}

	public List<RancherPod> getRancherPods(JSONObject projects, List<String> nameSpaceList) throws Exception {

		List<String> urls = new ArrayList<>();

		for (String nameSpace : nameSpaceList) {
			List<String> podUrls = getLink(projects, "pods", nameSpace);
			urls.addAll(podUrls);

			List<String> deploymentUrls = getLink(projects, "deployments", nameSpace);
			urls.addAll(deploymentUrls);
		}

		Map<String, JSONObject> map = getAsync(urls);

		List<JSONObject> pods = new ArrayList<>();

		for (String key : map.keySet()) {
			if (key.contains("pods")) {
				JSONObject pod = map.get(key);
				pods.add(pod);
			}
		}

		List<JSONObject> deployments = new ArrayList<>();

		for (String key : map.keySet()) {
			if (key.contains("deployments")) {
				JSONObject pod = map.get(key);
				deployments.add(pod);
			}
		}

		List<RancherPod> list = toRancherPods(pods, deployments);

		return list;
	}

	private void print(String format, Object... args) {

		if(!verbose) {
			return;
		}

		System.out.printf(format + "\n", args);
	}

	public Map<String, JSONObject> getAsync(List<String> urlList) throws InterruptedException {

		Map<String, JSONObject> map = new LinkedHashMap<>();

		CountDownLatch latch = new CountDownLatch(urlList.size());

		for (String url : urlList) {

			Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token).build();

			print("fetching url : %s", url);

			client.newCall(request).enqueue(new Callback() {

				@Override
				public void onResponse(Call call, Response response) throws IOException {

					String text = response.body().string();
					JSONObject data = JSON.parseObject(text);

					map.put(url, data);

					latch.countDown();
				}

				@Override
				public void onFailure(Call arg0, IOException ex) {
					ex.printStackTrace();
				}
			});

		}

		latch.await();

		return map;
	}

	public List<JSONObject> getData(JSONObject obj) {

		List<JSONObject> list = new ArrayList<>();

		JSONArray dataList = obj.getJSONArray("data");

		for (Object data : dataList) {

			JSONObject jo = (JSONObject) data;

			list.add(jo);
		}

		return list;
	}

	public List<RancherPod> toRancherPods(List<JSONObject> allPods, List<JSONObject> AllDeployments) {

		List<RancherPod> list = new ArrayList<>();

		for (JSONObject pods : allPods) {

			for (JSONObject pod : getData(pods)) {

				RancherPod p = new RancherPod();
				p.label = pod.getJSONObject("labels").getString("app");
				p.name = pod.getString("name");

				JSONObject status = pod.getJSONObject("status");
				p.state = status.getString("phase");
				p.nodeIp = status.getString("nodeIp");

				long startTime = status.getLong("startTimeTS");
				p.timeSince = getTimeSince(startTime);

				JSONArray containerStatuses = status.getJSONArray("containerStatuses");

				for (Object obj : containerStatuses) {
					JSONObject conState = (JSONObject) obj;

//					String restartCount = conState.getString("restartCount");
					boolean ready = conState.getBoolean("ready");
					p.ready = ready;
				}

				list.add(p);
			}
		}

		List<JSONObject> deploymentList = new ArrayList<>();

		for (JSONObject deployments : AllDeployments) {
			deploymentList.addAll(getData(deployments));
		}

		for (RancherPod pod : list) {

			for (JSONObject deployment : deploymentList) {

				String label = deployment.getJSONObject("labels").getString("app");

				if (pod.label.equals(label)) {

					pod.redeployUrl = deployment.getJSONObject("actions").getString("redeploy");

					JSONArray endPoints = deployment.getJSONArray("publicEndpoints");

					if (endPoints != null) {

						String address = "";
						int port = 0;

						for (Object obj : endPoints) {

							JSONObject ep = (JSONObject) obj;
							JSONArray ips = ep.getJSONArray("addresses");
							port = ep.getInteger("port");
							address = ips.getString(0);
						}

						print("%-50s %s:%s", label, address, port);

						if (!pod.nodeIp.contains(":")) {
							pod.nodeIp += ":" + port;
						}
					}

					JSONArray containers = deployment.getJSONArray("containers");

					for (Object obj : containers) {
						JSONObject con = (JSONObject) obj;

						pod.image = con.getString("image");
					}

					JSONObject deploymentStatus = deployment.getJSONObject("deploymentStatus");
					pod.replica = deploymentStatus.getInteger("availableReplicas");

					pod.nameSpaceId = deployment.getString("namespaceId");
				}
			}

		}

		print("");

		Collections.sort(list);

		return list;
	}

	private String getTimeSince(long start) {

		long elapsedTimeMillis = System.currentTimeMillis() - start;

		long days = TimeUnit.MILLISECONDS.toDays(elapsedTimeMillis);
		long hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMillis) % 24;
		long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis) % 60;

		if (days > 0) {
			return String.format("%2d days", days);
		}

		if (hours > 0) {
			return String.format("%2d hours", hours);
		}

		if (minutes > 0) {
			return String.format("%2d mins", minutes);
		}

		return String.format("%2d mins", minutes);
	}

	private static final String format = "%-60s %-10s %-10s %-10s %-10s %-25s %-50s";

	public void printHeader() {
		print(format, "Pod name", "replica", "ready", "state", "since", "address", "image");
	}

	public static class RancherPod implements Comparable<RancherPod> {

		String name;
		String nameSpaceId = "";
		boolean ready;
		String state;
		String timeSince;
		String image;
		String nodeIp;
		String label;
		int replica = -1;
		String redeployUrl;

		@Override
		public int compareTo(RancherPod app) {
			return name.compareTo(app.name);
		}

		@Override
		public String toString() {
			return String.format(format, name, replica, ready, state, timeSince, nodeIp, image);
		}
	}

	public void redeploy(List<RancherPod> list, String app) throws IOException {

		Set<String> urls = new HashSet<>();

		for (RancherPod pod : list) {
			if (pod.name.contains(app)) {

				String url = pod.redeployUrl;

				if (!urls.contains(url)) {
					print("Redeploy : %s", url);
					post(url);
				}

				urls.add(url);
			}
		}

	}

	private void post(String url) throws IOException {

		RequestBody body = RequestBody.create("{}", MediaType.parse("application/json"));
		Request request = new Request.Builder().url(url).addHeader("Authorization", "Bearer " + token).post(body).build();

		Response response = client.newCall(request).execute();

		String text = response.body().string();

		JSONObject data = JSON.parseObject(text);

		print("%s", data);
	}


}
