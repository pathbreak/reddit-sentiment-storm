package storm.redditsentiment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jline.internal.Log;

/**
 * Maintains comment counts for each story and finds top N lists when asked.
 *
 */
public class Summary {

	private static final long TIME_WINDOW = 3600000L; //ie, 60 minutes
	
	private Map<String, StoryData> stories = new HashMap<String, StoryData>();

	public void update(String subreddit, String storyId, String storyURL, String storyTitle, String commentId,
			String comment, int sentimentScore, long timestamp) {
		
		StoryData story = stories.get(storyId);
		if (story == null) {
			story = new StoryData();
			story.subreddit = subreddit;
			story.storyId = storyId;
			story.storyURL = storyURL;
			story.storyTitle = storyTitle;
			stories.put(storyId, story);
		}
		
		story.update(commentId, comment, sentimentScore, timestamp);
		
		// Discard stories that have not received any comment during the time window.
		long curTimestamp = System.currentTimeMillis();
		for (Iterator<StoryData> itr = stories.values().iterator(); itr.hasNext();) {
			StoryData s = itr.next();
			if (curTimestamp - s.lastCommentTimestamp > TIME_WINDOW) {
				Log.info("No comment received for {}:{}. Discarding", s.storyId, s.storyURL);
				itr.remove();
			}
		}
	}
	
	public List<StoryData> getMostCommentedStoriesInWindow(int topN) {
		List<StoryData> copy = new ArrayList<Summary.StoryData>();
		copy.addAll(stories.values());
		
		Collections.sort(copy, new Comparator<StoryData>() {

			public int compare(StoryData s1, StoryData s2) {
				// Return in descending order.
				return Integer.compare(s2.commentTimestamps.size(), s1.commentTimestamps.size());
			}
		});
		
		return copy.subList(0, Math.min(topN, copy.size()));
	}
	
	public List<StoryData> getMostPositiveCommentedStoriesInWindow(int topN) {
		
		List<StoryData> copy = new ArrayList<Summary.StoryData>();
		copy.addAll(stories.values());
		
		Collections.sort(copy, new Comparator<StoryData>() {

			public int compare(StoryData s1, StoryData s2) {
				// Return in descending order.
				return Integer.compare(s2.positiveCommentTimestamps.size(), s1.positiveCommentTimestamps.size());
			}
		});
		
		return copy.subList(0, Math.min(topN, copy.size()));
		
	}
	
	public List<StoryData> getMostNegativeCommentedStoriesInWindow(int topN) {
		
		List<StoryData> copy = new ArrayList<Summary.StoryData>();
		copy.addAll(stories.values());
		
		Collections.sort(copy, new Comparator<StoryData>() {

			public int compare(StoryData s1, StoryData s2) {
				// Return in descending order.
				return Integer.compare(s2.negativeCommentTimestamps.size(), s1.negativeCommentTimestamps.size());
			}
		});
		
		return copy.subList(0, Math.min(topN, copy.size()));
	}

	class StoryData {

		public String subreddit;
		public String storyId;
		public String storyURL;
		public String storyTitle;

		// Storing the last seen comment timestamp enables discarding the entire
		// story
		// if there hasn't been any new comment in last 1 hour.
		private long lastCommentTimestamp;

		// We store just the *timestamps* of each comment for this story in
		// these
		// lists.
		// The lengths of the lists tell us the required number of comments
		// statistic, while
		// storing their timestamps enables the removal of those outside the
		// time window of 1 hour.

		private LinkedList<Long> commentTimestamps = new LinkedList<Long>();
		private LinkedList<Long> positiveCommentTimestamps = new LinkedList<Long>();
		private LinkedList<Long> negativeCommentTimestamps = new LinkedList<Long>();
		
		public void update(String commentId, String comment, int sentimentScore, long timestamp) {
			this.lastCommentTimestamp = timestamp;
			
			this.commentTimestamps.add(timestamp);
			if (sentimentScore > 0) {
				this.positiveCommentTimestamps.add(timestamp);
				
			} else if (sentimentScore < 0) { 
				this.negativeCommentTimestamps.add(timestamp);
			}
			
			long curTimestamp = System.currentTimeMillis();
			long thresholdTimestamp = curTimestamp - TIME_WINDOW;   
			
			removeOutdated(commentTimestamps, thresholdTimestamp);
			removeOutdated(positiveCommentTimestamps, thresholdTimestamp);
			removeOutdated(negativeCommentTimestamps, thresholdTimestamp);
		}
		
		public int commentCount() {
			return commentTimestamps.size();
		}
		
		public long oldestCommentTimestamp() {
			if (commentTimestamps.size() > 0) {
				return commentTimestamps.getFirst();
			}
			return -1;
		}
		
		public int positiveCount() {
			return positiveCommentTimestamps.size();
		}
		
		public long oldestPositiveCommentTimestamp() {
			if (positiveCommentTimestamps.size() > 0) {
				return positiveCommentTimestamps.getFirst();
			}
			return -1;
		}
		
		public int negativeCount() {
			return negativeCommentTimestamps.size();
		}

		public long oldestNegativeCommentTimestamp() {
			if (negativeCommentTimestamps.size() > 0) {
				return negativeCommentTimestamps.getFirst();
			}
			return -1;
		}
		

		private void removeOutdated(LinkedList<Long> commentTimestamps, long thresholdTimestamp) {
			for (Iterator<Long> itr = commentTimestamps.iterator(); itr.hasNext();) {
				long ts = itr.next();
				if (ts < thresholdTimestamp) {
					itr.remove();
				} else if (ts >= thresholdTimestamp) {
					// Once a timestamps is found inside threshold, no need to check further because elements
					// are in chronological order.
					break;
				}
			}
		}
	}
}
