package fr.ac_versailles.crdp.apiscol.thumbs.automated;

import java.awt.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class PreferenceForNotOftenUsedLargeSquaresStrategy implements
		IThumbsChoiceStrategy {

	private static Map<String, Integer> usedUrls = new HashMap<String, Integer>();

	@Override
	public String selectBestThumb(HashMap<String, Point> suggestions,
			boolean acceptMetadataIcons) {
		Iterator<String> it = suggestions.keySet().iterator();
		double maxSize = -1;
		Point p;
		Set<String> bestUrls = new HashSet<String>();
		while (it.hasNext()) {
			String key = it.next();
			p = suggestions.get(key);
			double coeff = p.x * p.y - Math.pow(p.x - p.y, 2);
			if (coeff >= maxSize * 0.75) {
				bestUrls.add(key);
				maxSize = coeff;
			}
		}
		return lessOftenUsed(bestUrls);
	}

	private String lessOftenUsed(Set<String> bestUrls) {
		Integer bestScore = Integer.MAX_VALUE;
		Integer score = 0;
		String choosedUrl = "";
		for (String url : bestUrls) {
			if (usedUrls.containsKey(url))
				score = usedUrls.get(url);
			else
				score = 0;
			if (score < bestScore) {
				choosedUrl = url;
				bestScore = score;
			}
		}
		if (!usedUrls.containsKey(choosedUrl))
			usedUrls.put(choosedUrl, 0);
		else
			usedUrls.put(choosedUrl, usedUrls.get(choosedUrl)+1);
		return choosedUrl;
	}
}
