package storm.redditsentiment;

import org.apache.storm.zookeeper.WatchedEvent;
import org.apache.storm.zookeeper.Watcher;
import org.apache.storm.zookeeper.Watcher.Event.KeeperState;
import org.apache.storm.zookeeper.ZooKeeper;

/**
 * This console app registers with Zookeeper to receive updates published by SummarizerBolt to the "/reddit"
 * Znode and displays those comment summaries.
 * 
 */
public class ViewerApp {

	static ZooKeeper zk = null;

	public static void main(String[] args) {
		if (args == null || args.length < 2) {
			System.out.println("Missing command line arguments: [Zookeeper IP address]  [Zookeeper port]");
			System.exit(-1);
		}

		final Object barrier = new Object();

		String zkServer = args[0];
		String zkPort = args[1];
		try {

			zk = new ZooKeeper(zkServer + ":" + zkPort, 5000, new Watcher() {
				public void process(WatchedEvent e) {
					/*
					System.out.println("Main watch State:" + e.getState());
					System.out.println("Main watch Path:" + e.getPath());
					System.out.println("Main watch Event:" + e.getType());
					*/
					if (e.getState().equals(KeeperState.Disconnected)) {
						synchronized (barrier) {
							System.out.println("Disconnected");
							barrier.notify();
						}
					}

				}
			});
		} catch (Exception e) {
			//e.printStackTrace();
		}

		while (true) {

			try {
				if (zk.exists(ZkPublisher.ROOT_ZNODE, false) != null) {
					break;
				}
				System.out.println("Waiting for results to be published...");
				Thread.sleep(10000);

			} catch (Exception e) {

				System.out.println("Waiting for Zookeeper...");
				try {
					Thread.sleep(10000);
				} catch (InterruptedException ignored) {
				}
			}
		}

		System.out.println("Getting results");
		try {
			byte[] data = zk.getData(ZkPublisher.ROOT_ZNODE, new Watcher() {

				public void process(WatchedEvent e) {
					/*
					System.out.println("Data watch State:" + e.getState());
					System.out.println("Data watch Path:" + e.getPath());
					System.out.println("Data watch Event:" + e.getType());
					*/
					
					try {
						// ZK is weird in that the watch needs to be set up
						// again on every trigger.
						byte[] data = zk.getData(ZkPublisher.ROOT_ZNODE, this, null);
						String text = new String(data, "UTF-8");
						System.out.println(text);

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}, null);

			if (data != null) {
				String text = new String(data, "UTF-8");
				System.out.println(text);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		synchronized (barrier) {
			try {
				barrier.wait();
			} catch (InterruptedException e) {
				// e.printStackTrace();
			}
		}
		try {
			zk.close();
		} catch (InterruptedException ignore) {
		}
		
		System.out.println("Exiting");
	}

}
