package storm.redditsentiment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;


public class TestPrototype {
	public static void main(String[] args) throws Exception {
		
		BufferedReader reader = null;
		Map<String, Integer> sentiments = new HashMap<String, Integer>();
		try {
			reader = new BufferedReader(new InputStreamReader(
				TestPrototype.class.getClassLoader().getResourceAsStream("AFINN-111.txt")));
			
			String line = "";
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split("\t");
				String word = tokens[0];
				int value = Integer.parseInt(tokens[1]);
				sentiments.put(word, value);
			}
		} finally {
			if (reader != null) reader.close();
		}
		
		URL feedUrl = new URL(args[0]);
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed feed = input.build(new XmlReader(feedUrl));
		System.out.println("Fetched " + feed.getEntries().size());
		for (SyndEntry s : feed.getEntries()) {
			List<SyndContent> contents = s.getContents();
			
			String link = s.getLink();
			String storyURL = link.substring(0, link.lastIndexOf("/")); 
			String[] parts = storyURL.split("/");
			String storyId = parts[4];
			
			System.out.println("Link=" + link);
			System.out.println("StoryURL=" + storyURL);
			System.out.println("StoryID=" + storyId);
			System.out.println(s.getUri());
			
			String title = s.getTitle();
			String titlePrefix = s.getAuthor() + " on ";
			System.out.println("Story title=" + title.substring(titlePrefix.length(), title.length()));
			System.out.println("\n\n");
			if (contents != null && contents.size() > 0) {
				SyndContent cnt = contents.get(0);
				String comment = cnt.getValue();
				comment = Jsoup.clean(comment, Whitelist.none());
				comment = comment.replaceAll("\\p{Punct}", "");
				String[] tokens = comment.split("\\s+");
				//System.out.println(Arrays.toString(tokens));
				//System.out.println();
				int sentimentScore = 0;
				for (String t  : tokens) {
					if (t == null || t.isEmpty()) {
						continue;
					}
					Integer value = sentiments.get(t);
					if (value != null) {
						sentimentScore += value;
					}
				}
				System.out.println(Arrays.toString(tokens));
				System.out.println("Sentiment=" + sentimentScore);
			}
		}
	}
}
