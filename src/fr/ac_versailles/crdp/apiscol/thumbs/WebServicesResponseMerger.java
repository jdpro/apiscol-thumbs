package fr.ac_versailles.crdp.apiscol.thumbs;

import java.awt.Point;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.jersey.api.client.WebResource;

import fr.ac_versailles.crdp.apiscol.UsedNamespaces;
import fr.ac_versailles.crdp.apiscol.utils.LogUtility;

public class WebServicesResponseMerger {

	private final static ArrayList<String> exclusions = new ArrayList<String>();
	private final static ArrayList<String> ignoreList = new ArrayList<String>();
	static {
		exclusions.add("purpose");
		exclusions.add("lifeCycle");
		exclusions.add("relation");
		exclusions.add("size");
		exclusions.add("general");
		exclusions.add("metaMetadata");
		exclusions.add("description");
		exclusions.add("LOMv1.0");
		exclusions.add("LOMFRv1.0");
		exclusions.add("location");

		ignoreList.add("value");
		ignoreList.add("lomfr:value");
		ignoreList.add("source");
		ignoreList.add("string");
		ignoreList.add("orComposite");
		ignoreList.add("type");
	}

	private static NamespaceContext ctx = new NamespaceContext() {
		public String getNamespaceURI(String prefix) {
			String uri;
			if (prefix.equals(UsedNamespaces.ATOM.getShortHand()))
				uri = UsedNamespaces.ATOM.getUri();
			else if (prefix.equals(UsedNamespaces.APISCOL.getShortHand())) {
				uri = UsedNamespaces.APISCOL.getUri();
			} else
				uri = null;
			return uri;
		}

		public Iterator<String> getPrefixes(String val) {
			return null;
		}

		public String getPrefix(String uri) {
			return null;
		}
	};
	private static XPathFactory xPathFactory = XPathFactory.newInstance();
	private static XPath xpath = xPathFactory.newXPath();

	private static void assignNamespaceContext() {
		xpath.setNamespaceContext(ctx);
	}

	public static String extractThumbsLinkFromResourceRepresentation(
			Document contentResponse) {
		createLogger();
		assignNamespaceContext();
		XPathExpression exp;
		Element linkNode = null;
		try {
			exp = xpath.compile("//atom:link[@rel='icon']");
			linkNode = (Element) exp.evaluate(contentResponse,
					XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (linkNode == null)
			return null;
		return linkNode.getAttribute("href");
	}

	private static Logger logger;

	private static void createLogger() {
		if (logger == null)
			logger = LogUtility.createLogger(WebServicesResponseMerger.class
					.getCanonicalName());
	}

	public static String extractLomLinkFromMetadataRepresentation(
			Document metaResponse) {
		createLogger();
		assignNamespaceContext();
		XPathExpression exp;
		Element linkNode = null;
		try {
			exp = xpath
					.compile("//atom:link[@rel='describedby'][@type='application/lom+xml']");
			linkNode = (Element) exp
					.evaluate(metaResponse, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (linkNode == null)
			return null;
		return linkNode.getAttribute("href");
	}

	public static String extractCategoryFromMetadataRepresentation(
			Document metaResponse) {
		createLogger();
		assignNamespaceContext();
		XPathExpression exp;
		Element categoryNode = null;
		try {
			exp = xpath.compile("//atom:category");
			categoryNode = (Element) exp.evaluate(metaResponse,
					XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (categoryNode == null)
			return null;
		return categoryNode.getAttribute("term");
	}

	public static String extractContentLinkFromMetadataRepresentation(
			Document metaResponse) {
		createLogger();
		assignNamespaceContext();
		XPathExpression exp;
		Element linkNode = null;
		try {
			exp = xpath
					.compile("//atom:link[@rel='describes'][@type='application/atom+xml']");
			linkNode = (Element) exp
					.evaluate(metaResponse, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (linkNode == null)
			return null;
		return linkNode.getAttribute("href");
	}

	public static ArrayList<String> extractContentLinksFromPackageRepresentation(
			Document packResponse) {
		createLogger();
		assignNamespaceContext();
		ArrayList<String> list = new ArrayList<String>();
		XPathExpression exp;
		NodeList linkNodes = null;
		try {
			exp = xpath.compile("/atom:entry/atom:content/apiscol:resources/apiscol:resource");
			linkNodes = (NodeList) exp.evaluate(packResponse,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (linkNodes == null)
			return list;
		System.out.println(linkNodes.getLength());
		for (int j = 0; j < linkNodes.getLength(); j++) {
			Element link = (Element) linkNodes.item(j);
			String href = link.getAttribute("href");
			list.add(href);
		}
		return list;
	}

	public static void addThumbsFromMetadata(Document lomXML,
			HashMap<String, Point> thumbsList,
			WebResource metadataWebServiceResource) {
		ArrayList<String> concatenations = new ArrayList<String>();
		if (lomXML != null)
			populateRecursively(exclusions, ignoreList, concatenations,
					lomXML.getFirstChild(), "");
		Iterator<String> it = concatenations.iterator();
		while (it.hasNext()) {
			String filePath = String.format("%s.%s", (String) it.next(), "png");
			String url = String.format("%s%s%s", metadataWebServiceResource
					.getURI().toString(), "/icons/st0", filePath);
			if (iconExistOnMetaService(url)) {
				thumbsList.put(url, new Point());
			}
		}

	}

	private static boolean iconExistOnMetaService(String url) {
		URL u;
		try {
			u = new URL(url.replace("\n", ""));
			HttpURLConnection huc = (HttpURLConnection) u.openConnection();
			huc.setRequestMethod("GET");
			huc.connect();
			return huc.getResponseCode() == 200;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (ProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	private static void populateRecursively(ArrayList<String> exclusions,
			ArrayList<String> ignoreList, ArrayList<String> concatenations,
			Node node, String concatenation) {
		NodeList childs = node.getChildNodes();
		for (int i = 0; i < childs.getLength(); i++) {
			Node child = childs.item(i);
			if (exclusions.contains(child.getNodeName()))
				continue;
			if (child.getNodeName().equals("taxonPath")) {
				Element sourceElem = null;
				Element stringElem = null;
				Element taxonElem = null;
				Element idElem = null;
				String source;
				String id;
				try {
					sourceElem = (Element) ((Element) child)
							.getElementsByTagName("source").item(0);
					stringElem = (Element) (sourceElem
							.getElementsByTagName("string").item(0));
					source = stringElem.getTextContent();
					taxonElem = (Element) ((Element) child)
							.getElementsByTagName("taxon").item(0);
					idElem = (Element) (taxonElem.getElementsByTagName("id")
							.item(0));
					id = idElem.getTextContent();
					String result = concatenation + "/" + source + "/" + id;
					concatenations.add(result);
				} catch (Exception e) {
					// TODO: handle exception
				}

			} else if (child.hasChildNodes()) {
				String segment = ((Element) child).getTagName();
				if (!(ignoreList.contains(segment)))
					segment = concatenation + "/" + segment.toLowerCase();
				else
					segment = concatenation;
				populateRecursively(exclusions, ignoreList, concatenations,
						child, segment);
			} else {
				String textContent = child.getTextContent();
				if (StringUtils.isBlank(textContent))
					continue;
				if (exclusions.contains(textContent))
					continue;
				String result;
				try {
					result = concatenation
							+ "/"
							+ URLEncoder.encode(textContent.toLowerCase(),
									"UTF-8");
					concatenations.add(result);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

			}

		}

	}

	public static void addThumbsFromContent(Document contentResponse,
			HashMap<String, Point> thumbsList) {
		createLogger();
		assignNamespaceContext();
		XPathExpression exp;
		NodeList linkNodes = null;
		try {
			exp = xpath.compile("//apiscol:thumb/apiscol:link");
			linkNodes = (NodeList) exp.evaluate(contentResponse,
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}

		for (int j = 0; j < linkNodes.getLength(); j++) {
			Element link = (Element) linkNodes.item(j);
			String href = link.getAttribute("href");
			Element thumb = (Element) link.getParentNode();
			int x = 0, y = 0;
			if (thumb.hasAttribute("width"))
				x = (int) Math.round(Double.parseDouble(thumb
						.getAttribute("width")));
			if (thumb.hasAttribute("height"))
				y = (int) Math.round(Double.parseDouble(thumb
						.getAttribute("height")));
			if (!thumbsList.keySet().contains(href))
				thumbsList.put(href, new Point(x, y));

		}

	}

}
