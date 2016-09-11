package storm.redditsentiment;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.topology.BoltDeclarer;
import backtype.storm.topology.TopologyBuilder;

// Accepts list of subreddit names via command line, creates a Spout for 
// each subreddit, creates a couple of sentiment calculator bolts to process them,
// finally send comments and sentiment scores to SummarizerBolt for aggregation.
// Summarizer publishes summaries to a Zookeeper znode.
//
// Cmdline args for submitting to a remote Storm cluster: 
//		$ storm jar redditsentiment-cluster-all.jar storm.redditsentiment.RedditSentimentTopology 
//			<subreddit-1> <subreddit-2> ...
//
// Cmdline for running as a local standalone Storm cluster: 
//		$ java -jar redditsentiment-local-all.jar LOCAL <subreddit-1> <subreddit-2> ...

public class RedditSentimentTopology {
	
	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			System.out.println("Missing arguments: Expected a list of subreddits");
			System.exit(-1);
		}
		
		boolean localMode = false;
		if (args[0].equals("LOCAL")) {
			localMode = true;
			args = Arrays.copyOfRange(args, 1, args.length);
			System.out.println("Subreddits:" + Arrays.toString(args));
		}
		
		Map<String, Integer> sentimentData = null;
		try {
			sentimentData = SentimentData.getSentimentData();
		} catch (Exception e) {
			System.out.println("Error: Unable to read sentiment data");
			System.exit(-1);
		}
		
		TopologyBuilder topology = new TopologyBuilder();
		
		List<String> spoutIds = new ArrayList<String>();
		for (String subreddit : args) {
			// See if the feed is reachable.
			String subredditCommentsFeedURL = "https://www.reddit.com/r/" + subreddit + "/comments/.rss";
			try {
				SyndFeedInput input = new SyndFeedInput();
				input.build(new XmlReader(new URL(subredditCommentsFeedURL)));
				
			} catch (Exception e) {
				System.out.println("Error fetching comments for :" + subreddit + ". Skipping");
				continue;
			}
			
			String spoutId = "subredditspout-" + subreddit;
			spoutIds.add(spoutId);
			SubredditCommentsSpout spout = new SubredditCommentsSpout();
			
			topology.setSpout(spoutId, spout, 1)
				.addConfiguration("subreddit", subreddit)
				.addConfiguration("feedURL", subredditCommentsFeedURL);
		}
		BoltDeclarer sentiCalcBot = topology.setBolt("sentimentcalculator", new SentimentCalculatorBolt(), 
				spoutIds.size());
		for (String spoutId : spoutIds) {
			sentiCalcBot.shuffleGrouping(spoutId);
		}
		sentiCalcBot.addConfiguration("sentimentData", sentimentData);
		
		topology.setBolt("summarizer", new SummarizerBolt(), 1).globalGrouping("sentimentcalculator");
		
		Config conf = new Config();
		conf.setDebug(true);
		
		if (localMode) {
			System.out.println("Starting local cluster");
			LocalCluster localCluster = new LocalCluster();
			localCluster.submitTopology("redditsentiments", conf, topology.createTopology());
			
			final Object barrier = new Object();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					synchronized(barrier) {
						barrier.notify();
					}
				}
			});

			System.out.println("Press Ctrl+C to terminate");
			synchronized(barrier) {
				try {
					barrier.wait();
				} catch (InterruptedException e) {
				}
			}
			
			
			System.out.println("Shutting down local topology");
			localCluster.shutdown();
			
		} else {
			System.out.println("Submitting to Storm cluster");
			try {
				StormSubmitter.submitTopology("reddit", conf, topology.createTopology());
				
			} catch (AlreadyAliveException e) {
				e.printStackTrace();
			} catch (InvalidTopologyException e) {
				e.printStackTrace();
			}
		}
	}

}
