/**
 * Copyright 2010 Molindo GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.molindo.elasticsearch8105;

import java.io.File;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import at.molindo.utils.collections.ArrayUtils;
import at.molindo.utils.properties.SystemProperty;

public class Main implements Runnable, AutoCloseable {

	public static final File DIR = new File(
			SystemProperty.JAVA_IO_TMPDIR.getFile(), "elasticsearch8105");

	public static final String INDEX = "foobar";

	static {
		DIR.mkdirs();
	}

	private final Node _node;

	private boolean _wait;

	public static void main(String[] args) {

		boolean wait = !ArrayUtils.empty(args) && Boolean.parseBoolean(args[0]);
		try (Main main = new Main(wait)) {
			main.run();
		}
	}

	public Main(boolean wait) {
		_wait = wait;

		Settings settings = ImmutableSettings.settingsBuilder()
				.put("cluster.name", "elasticsearch8105")

				.put("path.data", new File(DIR, "data").toString())
				.put("path.logs", new File(DIR, "logs").toString())

				.put("node.data", true).put("node.local", true)

				.put("gateway.type", "local").put("http.enabled", false)

				.put("index.number_of_replicas", 0)
				.put("index.number_of_shards", 1)

				.build();

		_node = NodeBuilder.nodeBuilder().settings(settings).build();

		_node.start();
	}

	@Override
	public void run() {

		if (_wait) {
			waitForYellow();
		}
		
		if (!exists()) {
			create();
		}

	}

	private void waitForYellow() {
		ClusterHealthResponse response = _node.client().admin().cluster()
				.prepareHealth(INDEX).setWaitForYellowStatus()
				.setTimeout(TimeValue.timeValueSeconds(10)).execute()
				.actionGet();

		if (response.isTimedOut()
				|| response.getStatus() == ClusterHealthStatus.RED) {
			throw new IllegalStateException("cluster not ready");
		}
	}

	private boolean exists() {
		return _node.client().admin().indices().prepareExists(INDEX).execute()
				.actionGet().isExists();
	}

	private void create() {
		_node.client().admin().indices().prepareCreate(INDEX).execute()
				.actionGet();
	}

	@Override
	public void close() {
		_node.close();
	}
}
