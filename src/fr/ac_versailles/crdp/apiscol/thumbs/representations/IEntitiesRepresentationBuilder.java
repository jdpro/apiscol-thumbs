package fr.ac_versailles.crdp.apiscol.thumbs.representations;

import java.util.List;
import java.util.Set;

import javax.ws.rs.core.UriInfo;

import org.w3c.dom.Document;

public interface IEntitiesRepresentationBuilder<T> {

	T getThumbsInformationForMetadata(UriInfo uriInfo, String metadataId,
			String status);

	T getVoidSuggestion();

	T getThumbsRepresentation(UriInfo uriInfo,
			List<String> metadataList,
			String status);

	T getThumbsListRepresentation(Set<String> set,
			String metadataId, String etag,
			UriInfo uriInfo);
}
