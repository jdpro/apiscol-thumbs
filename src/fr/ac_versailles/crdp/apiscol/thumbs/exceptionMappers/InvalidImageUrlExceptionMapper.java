package fr.ac_versailles.crdp.apiscol.thumbs.exceptionMappers;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import fr.ac_versailles.crdp.apiscol.thumbs.InvalidImageUrlException;
import fr.ac_versailles.crdp.apiscol.thumbs.UnknownMetadataRepositoryException;

@Provider
public class InvalidImageUrlExceptionMapper implements
		ExceptionMapper<InvalidImageUrlException> {

	@Override
	public Response toResponse(InvalidImageUrlException e) {
		return Response.status(Status.BAD_REQUEST)
				.type(MediaType.APPLICATION_XML).entity(e.getXMLMessage())
				.build();
	}
}