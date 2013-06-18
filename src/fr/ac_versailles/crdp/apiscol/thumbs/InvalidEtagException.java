package fr.ac_versailles.crdp.apiscol.thumbs;

import fr.ac_versailles.crdp.apiscol.ApiscolException;

public class InvalidEtagException extends ApiscolException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public InvalidEtagException(String metadataId, String providedEtag) {
		super(
				String.format(
						"The provided etag %s for metadata %s does not match the thumb etag : refresh your data.",
						providedEtag, metadataId));
	}

}
