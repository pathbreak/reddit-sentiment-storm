package storm.redditsentiment;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.storm.zookeeper.CreateMode;
import org.apache.storm.zookeeper.WatchedEvent;
import org.apache.storm.zookeeper.Watcher;
import org.apache.storm.zookeeper.ZooDefs;
import org.apache.storm.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.Config;
import storm.redditsentiment.Summary.StoryData;

/**
 * Publishes Summary data to "/reddit" znode as displayable text to be consumed by Viewer app.
 *
 */
public class ZkPublisher {
	
	private static final Logger LOG = LoggerFactory.getLogger(ZkPublisher.class);

	public static final String ROOT_ZNODE = "/reddit";
	
	private ZooKeeper zkClient;
	
	
	public void init(Map stormConf) throws IOException {
		
		List<String> zkServers = (List<String>) stormConf.get(Config.STORM_ZOOKEEPER_SERVERS);
		int zkPort = ((Number) stormConf.get(Config.STORM_ZOOKEEPER_PORT)).intValue();
		
		StringBuilder connectString = new StringBuilder();
		for (int i = 0; i < zkServers.size(); i++) {
			connectString.append(zkServers.get(i)).append(":").append(zkPort);
			if (i < zkServers.size() - 1) {
				connectString.append(",");
			}
		}
		
		LOG.info("ZK connect string:{}", connectString);
		
		zkClient = new ZooKeeper(connectString.toString(), 5000, new Watcher() {
			public void process(WatchedEvent e) {
				LOG.info("Publisher Watcher thread [~{}]: {}", Thread.currentThread().getId(), e.toString());
			}	
		});
		
		try {
			if (zkClient.exists(ROOT_ZNODE, false) == null) {
				
				zkClient.create(ROOT_ZNODE, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);		// The Znode hierarchy is:
		}
	}
	
	public void publish(Summary summary) {
		StringBuilder summaryText = new StringBuilder(); 
		
		summaryText.append("\n\n=================================================================================\n");
		summaryText.append(new Date()).append("\n\n");
		
		List<StoryData> mostCommented = summary.getMostCommentedStoriesInWindow(5);
		summarize(mostCommented, summaryText, "MOST COMMENTED STORIES IN LAST 1 HOUR", 0);
		
		List<StoryData> mostPositive = summary.getMostPositiveCommentedStoriesInWindow(5);
		summarize(mostPositive, summaryText, "MOST POSITIVE COMMENT STORIES IN LAST 1 HOUR", 1);
		
		List<StoryData> mostNegative = summary.getMostNegativeCommentedStoriesInWindow(5);
		summarize(mostNegative, summaryText, "MOST NEGATIVE COMMENT STORIES IN LAST 1 HOUR", 2);
		
		summaryText.append("\n\n=================================================================================\n");
		
		try {
			zkClient.setData(ROOT_ZNODE, summaryText.toString().getBytes(), -1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void summarize(List<StoryData> stories, StringBuilder s, String title, int commentsType) { 
		s.append("\n\n").append(title).append(":\n\n");
		
		for (int rank = 0; rank < stories.size(); rank++) {
			StoryData story = stories.get(rank);
			
			int commentsCount = 0;
			long oldestCommentTimestamp = -1;
			
			switch (commentsType) {
				case 0:
					commentsCount = story.commentCount();
					oldestCommentTimestamp = story.oldestCommentTimestamp();
					break;
				case 1:
					commentsCount = story.positiveCount();
					oldestCommentTimestamp = story.oldestPositiveCommentTimestamp();
					break;
				case 2:
					commentsCount = story.negativeCount();
					oldestCommentTimestamp = story.oldestNegativeCommentTimestamp();
					break;
			}
			
			String url = "https://www.reddit.com" + story.storyURL;
			String commentSuffix = "";
			if (oldestCommentTimestamp != -1) {
				long period = (long)Math.ceil((System.currentTimeMillis() - oldestCommentTimestamp) / (1000.0 * 60.0));
				commentSuffix = " in last " + period + " minutes";
			}
			s.append(rank+1).append(". ").append(story.storyTitle).
				append("\n\t").append(commentsCount).append(" comments").append(commentSuffix).
				append("\n\t").append(url).append("\n\n");
		}
	}
	
	public void stop() {
		try {
			zkClient.close();
		} catch (InterruptedException ignore) {
			;
		}
	}
	
}
