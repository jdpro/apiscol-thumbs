package fr.ac_versailles.crdp.apiscol.thumbs.automated;

import java.awt.Point;
import java.util.HashMap;

public interface IThumbsChoiceStrategy {

	String selectBestThumb(HashMap<String, Point> suggestions, boolean acceptMetadataIcons);

}
