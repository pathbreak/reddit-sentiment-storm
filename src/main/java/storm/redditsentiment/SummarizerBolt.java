package storm.redditsentiment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;

/**
 * Receive sentiment scored comments from SentimentCalculators
 * and use Summary class to maintain a running count of comments and sentiments 
 * for last 1 hour against each story. 
 *
 */
public class SummarizerBolt extends BaseRichBolt {
	
	private static final Logger LOG = LoggerFactory.getLogger(SummarizerBolt.class);
	
	private OutputCollector collector;
	private String myId;
	
	private Summary summary;
	private ZkPublisher publisher;
	private long lastPublishedTimestamp; 
	
	public void execute(Tuple tuple) {
		String subreddit = tuple.getStringByField("subreddit");
		String storyId = tuple.getStringByField("storyid");
		String storyURL = tuple.getStringByField("storyurl");
		String storyTitle = tuple.getStringByField("storytitle");
		String commentId = tuple.getStringByField("commentid");
		String comment = tuple.getStringByField("comment");
		int sentimentScore = tuple.getIntegerByField("score");
		long timestamp = tuple.getLongByField("timestamp");

		LOG.info("Received {}:{}:{}:{}:{}:{}:[{}]", subreddit, sentimentScore, storyId, storyURL, 
				storyTitle, commentId, comment);
		
		collector.ack(tuple);
		
		summary.update(subreddit, storyId, storyURL, storyTitle, commentId,
				comment, sentimentScore, timestamp);
		
		// Publish updated statistics only every 30 secs.
		long curTime = System.currentTimeMillis();
		if (lastPublishedTimestamp == 0 ) {
			// Since messages come one by one to Summarizer, publishing immediately on first message
			// will show just 1 comment and looks odd. Instead, mark now as last published time
			// so that by next publishing window, we'd have received a couple of comments to show meaningful
			// rankings.
			lastPublishedTimestamp = curTime;
			
		} else if (curTime - lastPublishedTimestamp > 30000) {
			
			LOG.info("Publishing statistics to ZK");
			this.publisher.publish(summary);
			lastPublishedTimestamp = curTime;
		}
	}

	public void prepare(Map conf, TopologyContext ctx, OutputCollector collector) {
		
		this.collector = collector;
		this.myId = ctx.getThisComponentId() + "-" + ctx.getThisTaskId();
		
		this.summary = new Summary();
		
		this.publisher = new ZkPublisher();
		try {
			this.publisher.init(conf);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		this.lastPublishedTimestamp = 0;
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		// Summarizer has no output fields.
	}
	
	@Override
	public void cleanup() {
		// This is called only in local topology mode.
		super.cleanup();
		
		this.publisher.stop();
	}
	
}
