package storm.redditsentiment;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;

/**
 * A rather dumb and naive sentiment calculator that just sums up the AFINN scores of each word
 * in comment. There's no machine learning involved here. 
 * Can't recognize linguistic tones such as sarcasm.
 */
public class SentimentCalculatorBolt extends BaseRichBolt {

	private static final Logger LOG = LoggerFactory.getLogger(SentimentCalculatorBolt.class);
	
	private OutputCollector collector;
	
	// Notice that sentimentData map is a Map<String, Long> here.
	// Though sentimentData was loaded and added to configuration as a Map<String, Integer>,
	// when it's received at the bolt end, Storm seems to deserialize Map values as Longs,
	// probably something to do with clojure data type handling.
	// Trying to read value from it as a Map<String,Integer> results in "Long cannot be cast to Integer"
	// cast exceptions.
	private Map<String, Long> sentimentData;
	
	private String myId;
	
	public void execute(Tuple tuple) {
		String subreddit = tuple.getStringByField("subreddit");
		String storyId = tuple.getStringByField("storyid");
		String storyURL = tuple.getStringByField("storyurl");
		String storyTitle = tuple.getStringByField("storytitle");
		String commentId = tuple.getStringByField("commentid");
		String comment = tuple.getStringByField("comment");
		long timestamp = tuple.getLongByField("timestamp");
		
		LOG.info("Received {}:{}:{}:{}:{}:[{}]", subreddit, storyId, storyURL, storyTitle, commentId, comment);
		
		String[] tokens = comment.split("\\s+");
		int sentimentScore = 0;
		for (String t  : tokens) {
			if (t == null || t.isEmpty()) {
				continue;
			}
			Long value = sentimentData.get(t);
			if (value != null) {
				sentimentScore += value;
			}
		}
		collector.emit(tuple, new Values(subreddit, storyId, storyURL, storyTitle, 
				commentId, comment, sentimentScore, timestamp));
		LOG.info("Emit {}:{}:{}:{}:{}:{}:[{}]", subreddit, sentimentScore, storyId, storyURL, 
				storyTitle, commentId, comment);
		
		collector.ack(tuple);
	}

	public void prepare(Map conf, TopologyContext ctx, OutputCollector collector) {
		this.collector = collector;
		this.myId = ctx.getThisComponentId() + "-" + ctx.getThisTaskId();
		
		this.sentimentData = (Map<String, Long>) conf.get("sentimentData");
		if (this.sentimentData != null) {
			LOG.info("SentiCalcBolt " + myId + " has received sentimentData");
		}
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("subreddit", "storyid", "storyurl", "storytitle", 
				"commentid", "comment", "score", "timestamp"));
	}

}
