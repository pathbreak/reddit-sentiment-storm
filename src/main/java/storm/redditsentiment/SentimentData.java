package storm.redditsentiment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper for the AFINN sentiment dataset.
 *
 */
public class SentimentData {
	
	public static Map<String, Integer> getSentimentData() throws Exception {
		
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

		return sentiments;
	}
}
