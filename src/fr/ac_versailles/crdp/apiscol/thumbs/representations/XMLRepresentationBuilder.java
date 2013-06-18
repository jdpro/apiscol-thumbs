package fr.ac_versailles.crdp.apiscol.thumbs.representations;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fr.ac_versailles.crdp.apiscol.UsedNamespaces;
import fr.ac_versailles.crdp.apiscol.thumbs.ThumbsApi;
import fr.ac_versailles.crdp.apiscol.utils.XMLUtils;

public class XMLRepresentationBuilder extends
		AbstractRepresentationBuilder<Document> {

	@Override
	public Document getThumbsInformationForMetadata(UriInfo uriInfo,
			String metadataId, String status) {
		Document thumbs = createXMLDocument();

		Element rootElement = thumbs.createElement("thumbs");
		rootElement.setAttribute("mdid", metadataId);
		String etag = ThumbsApi.getThumbEtag(metadataId, status);
		rootElement.setAttribute("version", etag);

		Element thumbElement = thumbs.createElement("thumb");
		thumbElement.setAttribute("status", status);
		thumbElement.setTextContent(getFileUri(uriInfo, metadataId, status));
		rootElement.appendChild(thumbElement);

		thumbs.appendChild(rootElement);
		XMLUtils.addNameSpaces(thumbs, UsedNamespaces.APISCOL);
		return thumbs;
	}

	private static Document createXMLDocument() {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory
				.newInstance();
		docFactory.setNamespaceAware(true);
		DocumentBuilder docBuilder = null;
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Document doc = docBuilder.newDocument();
		return doc;
	}

	@Override
	public Document getVoidSuggestion() {
		Document thumbs = createXMLDocument();
		Element rootElement = thumbs.createElement("thumbs");
		thumbs.appendChild(rootElement);
		XMLUtils.addNameSpaces(thumbs, UsedNamespaces.APISCOL);
		return thumbs;
	}

	@Override
	public Document getThumbsListRepresentation(Set<String> thumbsList,
			String metadataId, String status, UriInfo uriInfo) {
		Document thumbs = createXMLDocument();
		Element rootElement = thumbs.createElement("thumbs");
		rootElement.setAttribute("mdid", metadataId);
		rootElement.setAttribute("version",
				ThumbsApi.getThumbEtag(metadataId, status));
		thumbs.appendChild(rootElement);
		Iterator<String> it = thumbsList.iterator();
		while (it.hasNext()) {
			Element thumbElement = thumbs.createElement("thumb");
			Element linkElement = thumbs.createElementNS(
					UsedNamespaces.ATOM.getUri(), "link");
			linkElement.setAttribute("href", it.next());
			rootElement.appendChild(thumbElement);
			thumbElement.appendChild(linkElement);
		}
		appendLinkElementForMetadata(metadataId, rootElement, status, uriInfo);
		XMLUtils.addNameSpaces(thumbs, UsedNamespaces.APISCOL);
		return thumbs;

	}

	@Override
	public Document getThumbsRepresentation(UriInfo uriInfo,
			List<String> metadataList, String status) {
		Document thumbs = createXMLDocument();
		Element rootElement = thumbs.createElement("thumbs");
		Iterator<String> it = metadataList.iterator();
		while (it.hasNext()) {
			String metadataId = (String) it.next();
			appendLinkElementForMetadata(metadataId, rootElement, status,
					uriInfo);

		}
		thumbs.appendChild(rootElement);
		XMLUtils.addNameSpaces(thumbs, UsedNamespaces.APISCOL);
		return thumbs;
	}

	private void appendLinkElementForMetadata(String metadataId,
			Element rootElement, String status, UriInfo uriInfo) {
		Element thumbElement = rootElement.getOwnerDocument().createElement(
				"thumb");
		thumbElement.setAttribute("version",
				ThumbsApi.getThumbEtag(metadataId, status));
		thumbElement.setAttribute("mdid", metadataId);
		rootElement.appendChild(thumbElement);
		Element linkElement = rootElement.getOwnerDocument().createElementNS(
				UsedNamespaces.APISCOL.getUri(), "link");
		String thumbImageUri = getFileUri(uriInfo, metadataId, status);
		if (StringUtils.isNotEmpty(thumbImageUri))
			linkElement.setAttribute("href", thumbImageUri);
		thumbElement.appendChild(linkElement);

	}

}
