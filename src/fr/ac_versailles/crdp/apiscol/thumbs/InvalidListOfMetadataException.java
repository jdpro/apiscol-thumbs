package fr.ac_versailles.crdp.apiscol.thumbs;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.sun.jersey.api.Responses;

import fr.ac_versailles.crdp.apiscol.utils.LogUtility;

public class InvalidListOfMetadataException extends WebApplicationException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger logger;
	{
		if (logger == null) {
			logger = LogUtility
					.createLogger(InvalidListOfMetadataException.class
							.getCanonicalName());
		}
	}

	public InvalidListOfMetadataException() {
		super(Responses.unsupportedMediaType().build());
	}

	public InvalidListOfMetadataException(String message) {
		super(
				Response.status(Response.Status.BAD_REQUEST)
						.entity(message).build());
		logger
				.error(message);

	}

}
