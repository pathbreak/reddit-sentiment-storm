package storm.redditsentiment;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

// Poll the feed URL every 30 seconds, get list of entries, discard
// duplicates already processed in 5 previous runs, and emit remaining entries. 
public class SubredditCommentsSpout extends BaseRichSpout {
	
	private static final Logger LOG = LoggerFactory.getLogger(SubredditCommentsSpout.class);
	
	private SpoutOutputCollector collector;
	private String subreddit;
	private URL subredditCommentsfeedURL;
	private ProcessedHistory history;
	private long lastFetchTimestamp = 0;
	
	public void open(Map conf, TopologyContext ctx, SpoutOutputCollector collector) {
		this.collector = collector;
		this.history = new ProcessedHistory();
		this.subreddit = (String) conf.get("subreddit");
		
		try {
			this.subredditCommentsfeedURL = new URL((String)conf.get("feedURL"));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		LOG.info("Spout subreddit:{} feedURL:{}", this.subreddit, this.subredditCommentsfeedURL);
		
		if (conf.containsKey("sentimentData")) {
			LOG.info("Spouts can also see sentimentData");
		}
	}

	public void nextTuple() {
		
		// Fetch feeds only every 30 secs.
		long curtime = System.currentTimeMillis();
		if (this.lastFetchTimestamp != 0) {
			if (curtime - this.lastFetchTimestamp < 30000) {
				// A Spout's nextTuple() is called continuously in a loop by Storm. If there's nothing to do,
				// just exit the method so Storm can do other things like acking processed messages.
				return;
			}
		}
		LOG.info("Fetching comments for " + subreddit + " at " + curtime);
		
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = null;
		try {
			feed = input.build(new XmlReader(this.subredditCommentsfeedURL));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		this.lastFetchTimestamp = System.currentTimeMillis();
		LOG.info("Fetched " + feed.getEntries().size() + " comments for " + subreddit + " at " + this.lastFetchTimestamp);
		
		history.startBatch();
		
		for (SyndEntry s : feed.getEntries()) {
			String commentId = s.getUri();
			if (history.contains(commentId)) {
				LOG.info("Skip dupe " + subreddit + ":" + commentId);
				continue;
			}
			
			// An entry.link has the syntax:
			//	/r/[SUBREDDIT]/comments/[STORY-ID]/[STORY-PATH]/[COMMENT-ID]
			// We extract the story ID and story URL (that is everything except the [COMMENT-ID] at the end.
			// 
			// Story title can be extracted from entry.title which has the syntax:
			//	[AUTHOR] on [STORY TITLE]
			
			List<SyndContent> contents = s.getContents();
			if (contents != null && contents.size() > 0) {
				
				String link = s.getLink();
				String storyURL = link.substring(0, link.lastIndexOf("/")); 
				String[] parts = storyURL.split("/");
				String storyId = parts[4];
				
				String title = s.getTitle();
				String titlePrefix = s.getAuthor() + " on ";
				String storyTitle = title.substring(titlePrefix.length(), title.length());
				
				SyndContent cnt = contents.get(0);
				String comment = cnt.getValue();
				comment = Jsoup.clean(comment, Whitelist.none());
				comment = comment.replaceAll("\\p{Punct}", "");
				LOG.info("Emit {}:{}:{}:{}:{}:[{}]", subreddit, storyId, storyURL, storyTitle, commentId, comment);
				collector.emit(
						new Values(subreddit, storyId, storyURL, storyTitle, commentId, comment, this.lastFetchTimestamp), 
						commentId);
			}
			
			history.add(commentId);
			
		}
		
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("subreddit", "storyid", "storyurl", 
				"storytitle", "commentid", "comment", "timestamp"));
	}

	/**
	 * 
	 * Keeps track of comments which are already processed, to avoid duplication.
	 * 
	 * Implementation:
	 * - Maintains history of 5 previous fetches in chronological order.
	 * 
	 * - When a comment is received, the previous batch is checked.
	 *   If found in that batch, spout does not process it again.
	 *   If not found, then the batch previous to previous is checked.
	 *   ...and so on till the first batch 
	 *   
	 * - If it's not in any of the batches, then it's processed.
	 * 
	 * - Alternate implementation using timestamps is probably more reliable,
	 *   but not worth the effort.
	 */
	class ProcessedHistory {
		
		// Array of 5 Sets corresponding to previous 5 feed fetches.
		// Each is a Set of the message IDs in that batch.
		// 1st element in array is the last fetched batch.
		// 2nd element is the batch fetched earlier to that.
		// ...
		// Last element is the oldest batch in history.
		// As a new batch is started, the oldest batch is discarded, all 
		// the other batches are moved down 1 index, and new batch is inserted into
		// 1st Set.
		
		private Set[] batches = new Set[5];
		private int oldestBatch = -1; 
		
		public void startBatch() {
			if (oldestBatch >= 0) {
				
				// If oldest batch is already the last batch that is discarded,
				// then just mark the second oldest batch as oldest.
				if (oldestBatch == batches.length - 1) {
					oldestBatch = oldestBatch - 1;
				}
				
				// Move every batch down one index.
				for (int i = oldestBatch; i >= 0; i--) {
					batches[i+1] = batches[i];  
				}
			}
			
			// Advance the oldestBatch by 1 index.
			oldestBatch += 1;
			
			// Start the new batch.
			batches[0] = new HashSet<String>();
		}
		
		public boolean contains(String commentId) {
			for (int i = 0; i <= oldestBatch; i++) {
				if (batches[i].contains(commentId)) {
					return true;
				}
			}
			return false;
		}
		
		public void add(String commentId) {
			batches[0].add(commentId);
		}
	}
}
