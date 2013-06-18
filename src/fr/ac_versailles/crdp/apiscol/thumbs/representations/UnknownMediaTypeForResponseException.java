package fr.ac_versailles.crdp.apiscol.thumbs.representations;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;

import com.sun.jersey.api.Responses;

import fr.ac_versailles.crdp.apiscol.utils.LogUtility;

public class UnknownMediaTypeForResponseException extends
		WebApplicationException {
	private static Logger webApplicationExceptionLogger;
	{
		if (webApplicationExceptionLogger == null) {
			webApplicationExceptionLogger = LogUtility.createLogger(WebApplicationException.class
					.getCanonicalName());
		}
	}

	public UnknownMediaTypeForResponseException() {
		super(Responses.unsupportedMediaType().build());
	}

	public UnknownMediaTypeForResponseException(String mediaType) {
		super(
				Response.status(Response.Status.NOT_ACCEPTABLE)
						.entity(String
								.format("There are no responses provided for the requested mediatype %s.",
										mediaType)).type("text/plain").build());		
		webApplicationExceptionLogger.error("There are no responses provided for the requested mediatype "+mediaType);
		
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
