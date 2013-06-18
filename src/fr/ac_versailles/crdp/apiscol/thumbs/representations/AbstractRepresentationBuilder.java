package fr.ac_versailles.crdp.apiscol.thumbs.representations;

import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import fr.ac_versailles.crdp.apiscol.thumbs.ThumbsApi;
import fr.ac_versailles.crdp.apiscol.thumbs.fileSystemAccess.ResourceDirectoryInterface;
import fr.ac_versailles.crdp.apiscol.utils.FileUtils;
import fr.ac_versailles.crdp.apiscol.utils.LogUtility;

public abstract class AbstractRepresentationBuilder<T> implements
		IEntitiesRepresentationBuilder<T> {
	protected static Logger logger;

	public AbstractRepresentationBuilder() {
		createLogger();
	}

	private void createLogger() {
		if (logger == null)
			logger = LogUtility
					.createLogger(this.getClass().getCanonicalName());

	}

	protected String getFileUri(UriInfo uriInfo, String metadataId,
			String status) {
		String thumbId = ThumbsApi.getThumbId(metadataId, status);
		String fileName = ResourceDirectoryInterface
				.getFileName(thumbId);
		if (!StringUtils.isEmpty(fileName))
			return convertToUrl(fileName, uriInfo);
		else
			return StringUtils.EMPTY;
	}

	private String convertToUrl(String imageName, UriInfo uriInfo) {
		return (new StringBuilder().append(uriInfo.getBaseUri())
				.append(FileUtils.getFilePathHierarchy("files", imageName)))
				.toString();
	}

}
