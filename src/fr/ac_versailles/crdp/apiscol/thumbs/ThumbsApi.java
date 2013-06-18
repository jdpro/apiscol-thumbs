package fr.ac_versailles.crdp.apiscol.thumbs;

import java.awt.Point;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.multipart.FormDataParam;

import fr.ac_versailles.crdp.apiscol.ApiscolApi;
import fr.ac_versailles.crdp.apiscol.ParametersKeys;
import fr.ac_versailles.crdp.apiscol.ResourcesKeySyntax;
import fr.ac_versailles.crdp.apiscol.thumbs.automated.IThumbsChoiceStrategy;
import fr.ac_versailles.crdp.apiscol.thumbs.automated.PreferenceForNotOftenUsedLargeSquaresStrategy;
import fr.ac_versailles.crdp.apiscol.thumbs.fileSystemAccess.ResourceDirectoryInterface;
import fr.ac_versailles.crdp.apiscol.thumbs.representations.EntitiesRepresentationBuilderFactory;
import fr.ac_versailles.crdp.apiscol.thumbs.representations.IEntitiesRepresentationBuilder;
import fr.ac_versailles.crdp.apiscol.transactions.KeyLock;
import fr.ac_versailles.crdp.apiscol.utils.XMLUtils;

@Path("/")
public class ThumbsApi extends ApiscolApi {

	@Context
	private ServletContext context;
	@Context
	private UriInfo uriInfo;

	private static int automatedChoiceDelay = 15;
	private static int maxNumberOfTries = 8;

	private static final String DEFAULT_STATUS = "default";

	private static boolean isInitialized = false;
	private static Client client;
	private static WebResource contentWebServiceResource;
	private static WebResource metadataWebServiceResource;
	private static WebResource packWebServiceResource;
	private static URI contentWebserviceUrl;
	private static ScheduledExecutorService delayedThumbChoiceExecutor = Executors
			.newSingleThreadScheduledExecutor();

	private static DocumentBuilder builder;

	private static HashMap<String, Boolean> automatedProcessesStop = new HashMap<String, Boolean>();
	private IThumbsChoiceStrategy thumbsChoiceStrategy = new PreferenceForNotOftenUsedLargeSquaresStrategy();

	public ThumbsApi(@Context ServletContext context) {
		super(context);
		if (!isInitialized) {
			initializeResourceDirectoryInterface(context);
			createWebServiceClients(context);
			createXMLBuilder();
			initializeStaticParameters(context);
			isInitialized = true;
		}
	}

	private void initializeStaticParameters(ServletContext context) {
		maxNumberOfTries = Integer.parseInt(getProperty(
				ParametersKeys.automaticThumbChoiceMaxNumberOfTries, context));
		automatedChoiceDelay = Integer.parseInt(getProperty(
				ParametersKeys.automaticThumbChoiceDelay, context));
	}

	private void createXMLBuilder() {
		DocumentBuilderFactory factory;
		try {
			factory = DocumentBuilderFactory.newInstance();
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}

	}

	private void initializeResourceDirectoryInterface(ServletContext context) {
		if (!ResourceDirectoryInterface.isInitialized())
			ResourceDirectoryInterface.initialize(getProperty(
					ParametersKeys.fileRepoPath, context));
	}

	private void createWebServiceClients(ServletContext context) {
		client = Client.create();

		URI metadataWebserviceUrl = null;
		URI packWebserviceUrl = null;
		try {
			contentWebserviceUrl = new URI(getProperty(
					ParametersKeys.contentWebserviceUrl, context));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		try {
			metadataWebserviceUrl = new URI(getProperty(
					ParametersKeys.metadataWebserviceUrl, context));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		try {
			packWebserviceUrl = new URI(getProperty(
					ParametersKeys.packWebserviceUrl, context));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		contentWebServiceResource = client.resource(UriBuilder.fromUri(
				contentWebserviceUrl).build());
		metadataWebServiceResource = client.resource(UriBuilder.fromUri(
				metadataWebserviceUrl).build());
		packWebServiceResource = client.resource(UriBuilder.fromUri(
				packWebserviceUrl).build());

	}

	@GET
	@Path("/suggestions")
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML,
			MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML })
	public Response getThumbSuggestionsForMetadata(
			@Context HttpServletRequest request,
			@QueryParam(value = "mdid") String metadataId,
			@QueryParam(value = "format") final String format) {
		String requestedFormat = guessRequestedFormat(request, format);
		if (StringUtils.isEmpty(metadataId)
				|| !metadataId.startsWith(metadataWebServiceResource.getURI()
						.toString()))
			return Response
					.status(Status.BAD_REQUEST)
					.entity("This apiscol instance does handle iconification only for this metadata repository "
							+ metadataWebServiceResource.getURI().toString()
							+ ", your metadata id does not match :"
							+ metadataId).build();
		IEntitiesRepresentationBuilder<?> rb = EntitiesRepresentationBuilderFactory
				.getRepresentationBuilder(requestedFormat, context);

		HashMap<String, Point> thumbsList = getThumbSuggestions(metadataId,
				true);
		return Response.ok(
				rb.getThumbsListRepresentation(thumbsList.keySet(), metadataId,
						DEFAULT_STATUS, uriInfo)).build();
	}

	private HashMap<String, Point> getThumbSuggestions(String metadataId,
			boolean acceptMetadataIcons) {
		HashMap<String, Point> thumbsList = new HashMap<String, Point>();

		String metadataUUID = metadataId.replace(metadataWebServiceResource
				.getURI().toString() + "/", "");
		Document metaResponse = null;
		ClientResponse metadataWebServiceResponse = metadataWebServiceResource
				.path(metadataUUID).queryParam("desc", "true")
				.accept(MediaType.APPLICATION_XML_TYPE)
				.get(ClientResponse.class);
		if (metadataWebServiceResponse.getStatus() == Status.OK.getStatusCode())
			metaResponse = metadataWebServiceResponse.getEntity(Document.class);
		else {
			String error = metadataWebServiceResponse.getEntity(String.class);
			logger.error("The metadata web service was not able to send response : "
					+ error);
		}
		String aggregationLevel = WebServicesResponseMerger
				.extractCategoryFromMetadataRepresentation(metaResponse);

		/*
		 * first ask meta service for the whole lom file
		 */
		if (metaResponse != null && acceptMetadataIcons) {
			String lomLink = WebServicesResponseMerger
					.extractLomLinkFromMetadataRepresentation(metaResponse);
			if (StringUtils.isNotEmpty(lomLink)) {
				Document lomXML = getXMLDocumentFromUrl(lomLink);
				if (lomXML != null)
					WebServicesResponseMerger.addThumbsFromMetadata(lomXML,
							thumbsList, metadataWebServiceResource);
			}
		}

		/*
		 * IF IT IS A LEARNING OBJECT, ask the content service for contents with
		 * desired metadata
		 */
		if (aggregationLevel.trim().equals("learning object")) {
			MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
			queryParams.add("mdid", metadataId);
			Document contentFirstResponse = null;
			ClientResponse contentWebServiceFirstResponse = contentWebServiceResource
					.path("resource").queryParams(queryParams)
					.accept(MediaType.APPLICATION_XML_TYPE)
					.get(ClientResponse.class);
			if (contentWebServiceFirstResponse.getStatus() == Status.OK
					.getStatusCode()) {
				contentFirstResponse = contentWebServiceFirstResponse
						.getEntity(Document.class);
				addThumbsFromContent(thumbsList, contentFirstResponse);
			} else {
				String error = contentWebServiceFirstResponse
						.getEntity(String.class);
				logger.error("The content web service was not able to send first response : "
						+ error);

			}

		}
		/*
		 * IF IT IS A LEARNING OBJECT, ask the pack service for contents list
		 */
		if (aggregationLevel.trim().equals("lesson")) {
			String packLink = WebServicesResponseMerger
					.extractContentLinkFromMetadataRepresentation(metaResponse);
			if (!packLink
					.startsWith(packWebServiceResource.getURI().toString()))
				logger.error("This packages repository is not handled by this thumb web service :"
						+ packLink);
			else {
				String manifestId = packLink.replace(packWebServiceResource
						.getURI().toString() + "/manifest/", "");
				Document packResponse = null;
				ClientResponse packWebServiceResponse = packWebServiceResource
						.path("manifest").path(manifestId)
						.queryParam("desc", "true")
						.accept(MediaType.APPLICATION_XML_TYPE)
						.get(ClientResponse.class);
				if (packWebServiceResponse.getStatus() == Status.OK
						.getStatusCode()) {
					packResponse = packWebServiceResponse
							.getEntity(Document.class);
					ArrayList<String> resourcesLinks = WebServicesResponseMerger
							.extractContentLinksFromPackageRepresentation(packResponse);
					Iterator<String> it = resourcesLinks.iterator();
					while (it.hasNext()) {
						String contentLink = (String) it.next();
						if (!contentLink.startsWith(contentWebserviceUrl
								.toString())) {
							logger.error("This content url is not handled by this metadata instance : "
									+ contentLink);
							continue;
						}
						String contentId = contentLink.replace(
								contentWebserviceUrl.toString() + "/resource/",
								"");
						Document contentFirstResponse = null;
						ClientResponse contentWebServiceFirstResponse = contentWebServiceResource
								.path("resource").path(contentId)
								.accept(MediaType.APPLICATION_XML_TYPE)
								.get(ClientResponse.class);
						if (contentWebServiceFirstResponse.getStatus() == Status.OK
								.getStatusCode()) {
							contentFirstResponse = contentWebServiceFirstResponse
									.getEntity(Document.class);
							addThumbsFromContent(thumbsList,
									contentFirstResponse);
						} else {
							String error = contentWebServiceFirstResponse
									.getEntity(String.class);
							logger.error("The content web service was not able to send first response : "
									+ error);

						}
					}
				} else {
					String error = packWebServiceResponse
							.getEntity(String.class);
					logger.error("The pack web service was not able to send response : "
							+ error);

				}
			}
		}

		return thumbsList;

	}

	private void addThumbsFromContent(HashMap<String, Point> thumbsList,
			Document contentRepresentation) {

		String thumbLink = WebServicesResponseMerger
				.extractThumbsLinkFromResourceRepresentation(contentRepresentation);

		/*
		 * then ask the content service for thumbs suggestions for this contents
		 */
		if (StringUtils.isNotEmpty(thumbLink)) {
			String iconsPath = thumbLink.replace(
					contentWebserviceUrl.toString(), "");
			Document contentSecondResponse = null;
			ClientResponse contentWebServiceSecondResponse = contentWebServiceResource
					.path(iconsPath).accept(MediaType.APPLICATION_XML_TYPE)
					.get(ClientResponse.class);
			if (contentWebServiceSecondResponse.getStatus() == Status.OK
					.getStatusCode())
				contentSecondResponse = contentWebServiceSecondResponse
						.getEntity(Document.class);
			else {
				String error = contentWebServiceSecondResponse
						.getEntity(String.class);
				logger.warn(String
						.format("Content web service was asked for thumbs suggestions for resource but he sent this code : %s with message : %s",
								contentWebServiceSecondResponse.getStatus(),
								error));
			}
			WebServicesResponseMerger.addThumbsFromContent(
					contentSecondResponse, thumbsList);
		}

	}

	private Document getXMLDocumentFromUrl(String lomLink) {
		URL dataURL = null;
		try {
			dataURL = new URL(lomLink);
		} catch (MalformedURLException e1) {
			logger.error(String.format("This string is not a valid url %s",
					lomLink));
			return null;
		}

		InputStream iStream = null;
		try {
			iStream = dataURL.openStream();
		} catch (IOException e1) {
			logger.error(String.format(
					"Impossible to connect to this url url %s", lomLink));
			return null;
		}

		Document ret = null;

		try {
			ret = builder.parse((new InputSource(iStream)));
		} catch (SAXException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return ret;
	}

	@PUT
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML })
	public Response setThumbForMetadata(
			@Context HttpServletRequest request,
			@QueryParam(value = "mdid") final String metadataId,
			@QueryParam(value = "format") final String format,
			@QueryParam(value = "src") final String imageUrl,
			@DefaultValue("false") @QueryParam(value = "auto") final String auto,
			@DefaultValue(DEFAULT_STATUS) @QueryParam(value = "status") final String status)
			throws InvalidImageUrlException, InvalidEtagException {
		String requestedFormat = guessRequestedFormat(request, format);
		String providedEtag = request.getHeader(HttpHeaders.IF_MATCH);
		takeAndReleaseGlobalLock();
		KeyLock keyLock = null;
		try {
			keyLock = keyLockManager.getLock(metadataId);
			keyLock.lock();
			try {
				logger.info(String
						.format("Entering critical section with mutual exclusion for metadata %s",
								metadataId));
				checkFreshness(providedEtag, metadataId, status);
				Boolean autoParam = StringUtils.equals(auto, "true");
				String thumbId = getThumbId(metadataId, status);
				String url = "";
				String bestUrl;
				// if the is an automatic thumb choice request,
				// try to get an image but don't include the metadata icons
				// first.
				if (autoParam) {
					bestUrl = selectMostAppropriateThumb(metadataId, false);
					if (StringUtils.isNotBlank(bestUrl)) {
						url = bestUrl;
					}
				} else
					url = imageUrl;
				if (StringUtils.isNotBlank(url)) {
					registerUrl(url, metadataId, status);
				} else {
					ResourceDirectoryInterface.assignRandomThumb(thumbId);
					// contains flag that indicate to executor if the process of
					// thumb research must be stopped for this metadata id
					synchronized (automatedProcessesStop) {
						automatedProcessesStop.put(metadataId, false);
					}
					delayedThumbChoiceExecutor.schedule(
							new ThumbDelayedChooserBuilder()
									.metadata(metadataId)
									.etag(getThumbEtag(metadataId, status))
									.numberOfTries(0).status(status)
									.askForMetadataIcons(false).build(),
							automatedChoiceDelay, TimeUnit.SECONDS);
				}
			} finally {
				keyLock.unlock();
			}
		} finally {
			if (keyLock != null) {
				keyLock.release();
			}
			logger.info(String
					.format("Leaving critical section with mutual exclusion for metadata %s",
							metadataId));
		}

		IEntitiesRepresentationBuilder<?> rb = EntitiesRepresentationBuilderFactory
				.getRepresentationBuilder(requestedFormat, context);
		return Response
				.ok(rb.getThumbsInformationForMetadata(uriInfo, metadataId,
						status)).build();
	}

	private void registerUrl(String url, String metadataId, String status)
			throws InvalidImageUrlException {
		eraseThumb(metadataId, status);
		ResourceDirectoryInterface.storeAndResizeThumb(
				getThumbId(metadataId, status), url);

	}

	private String selectMostAppropriateThumb(String metadataId,
			boolean acceptMetadataIcons) {
		HashMap<String, Point> suggestions = getThumbSuggestions(metadataId,
				acceptMetadataIcons);
		return thumbsChoiceStrategy.selectBestThumb(suggestions,
				acceptMetadataIcons);
	}

	@DELETE
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML })
	public Response deleteThumbForMetadata(
			@Context HttpServletRequest request,
			@QueryParam(value = "mdid") final String metadataId,
			@QueryParam(value = "format") final String format,
			@DefaultValue(DEFAULT_STATUS) @QueryParam(value = "status") final String status)
			throws InvalidImageUrlException, InvalidEtagException {
		String requestedFormat = guessRequestedFormat(request, format);
		takeAndReleaseGlobalLock();
		KeyLock keyLock = null;
		try {
			keyLock = keyLockManager.getLock(metadataId);
			keyLock.lock();
			try {
				logger.info(String
						.format("Entering critical section with mutual exclusion for metadata %s",
								metadataId));
				checkFreshness(request.getHeader(HttpHeaders.IF_MATCH),
						metadataId, status);
				synchronized (automatedProcessesStop) {
					automatedProcessesStop.put(metadataId, true);
				}
				eraseThumb(metadataId, status);

			} finally {
				keyLock.unlock();
			}
		} finally {
			if (keyLock != null) {
				keyLock.release();
			}
			logger.info(String
					.format("Leaving critical section with mutual exclusion for metadata %s",
							metadataId));
		}
		IEntitiesRepresentationBuilder<?> rb = EntitiesRepresentationBuilderFactory
				.getRepresentationBuilder(requestedFormat, context);
		return Response
				.ok(rb.getThumbsInformationForMetadata(uriInfo, metadataId,
						status)).build();
	}

	@POST
	@Produces({ MediaType.APPLICATION_ATOM_XML, MediaType.APPLICATION_XML })
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response sendCustomThumb(
			@Context HttpServletRequest request,
			@FormDataParam("image") InputStream uploadedInputStream,
			@FormDataParam("image") FormDataContentDisposition fileDetail,
			@FormDataParam("mdid") String metadataId,
			@FormDataParam("fname") String fileName,
			@DefaultValue(DEFAULT_STATUS) @FormDataParam(value = "status") final String status)
			throws InvalidEtagException, InvalidImageUrlException {
		takeAndReleaseGlobalLock();
		KeyLock keyLock = null;
		try {
			keyLock = keyLockManager.getLock(metadataId);
			keyLock.lock();
			try {
				logger.info(String
						.format("Entering critical section with mutual exclusion for metadata %s",
								metadataId));
				checkFreshness(request.getHeader(HttpHeaders.IF_MATCH),
						metadataId, status);
				String thumbId = getThumbId(metadataId, status);
				eraseThumb(metadataId, status);
				ResourceDirectoryInterface.storeAndResizeCustomThumb(thumbId,
						uploadedInputStream, fileDetail);
			} finally {
				keyLock.unlock();
			}
		} finally {
			if (keyLock != null) {
				keyLock.release();
			}
			logger.info(String
					.format("Leaving critical section with mutual exclusion for metadata %s",
							metadataId));
		}
		IEntitiesRepresentationBuilder<?> rb = EntitiesRepresentationBuilderFactory
				.getRepresentationBuilder(MediaType.APPLICATION_ATOM_XML,
						context);
		return Response
				.ok(rb.getThumbsInformationForMetadata(uriInfo, metadataId,
						status)).build();

	}

	private void eraseThumb(String metadataId, String status) {
		String thumbId = getThumbId(metadataId, status);
		if (ResourceDirectoryInterface.eraseThumb(thumbId))
			logger.info(String
					.format("The old thumb has been erased for metadata %s",
							metadataId));
		else
			logger.info(String.format(
					"There was no old thumb to erase for metadata %s",
					metadataId));

	}

	public static String getThumbId(String metadataId, String status) {
		String thumbId = hashMD5AndConvertToString(new StringBuilder()
				.append(metadataId).append(status).toString());
		return thumbId;
	}

	public static String hashMD5AndConvertToString(String text) {
		byte[] hash = null;
		try {
			hash = MessageDigest.getInstance("MD5").digest(text.getBytes());
		} catch (NoSuchAlgorithmException e) {
			// impossible
			e.printStackTrace();
		}
		BigInteger bi = new BigInteger(1, hash);
		String result = bi.toString(16);
		if (result.length() % 2 != 0) {
			return "0" + result;
		}
		return result;
	}

	@GET
	@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_ATOM_XML,
			MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML })
	public Response getThumbsForMetadata(
			@Context HttpServletRequest request,
			@DefaultValue("{}") @QueryParam(value = "mdids") String metadataIds,
			@QueryParam(value = "format") final String format,
			@DefaultValue(DEFAULT_STATUS) @QueryParam(value = "status") final String status)
			throws UnknownMetadataRepositoryException, UnknownMetadataException {
		String requestedFormat = guessRequestedFormat(request, format);
		if (!status.equals(DEFAULT_STATUS))
			return Response
					.status(Status.BAD_REQUEST)
					.entity("This thumb status is not accepted at this time "
							+ status).build();
		java.lang.reflect.Type collectionType = new TypeToken<List<String>>() {
		}.getType();
		List<String> metadataList = null;
		if (!metadataIds.startsWith("[")) {
			metadataList = new ArrayList<String>();
			metadataList.add(metadataIds);
		} else {
			try {
				metadataList = new Gson().fromJson(metadataIds, collectionType);
			} catch (Exception e) {
				String message = String
						.format("The list of metadata %s is impossible to parse as JSON",
								metadataIds);
				logger.warn(message);
				throw new InvalidListOfMetadataException(message);
			}
		}
		Iterator<String> it = metadataList.iterator();
		while (it.hasNext()) {
			String metadataId = ResourcesKeySyntax
					.removeSSL((String) it.next());
			if (!metadataId.startsWith(metadataWebServiceResource.getURI()
					.toString()))
				throw new UnknownMetadataRepositoryException(
						"This apiscol instance does handle iconification only for this metadata repository "
								+ metadataWebServiceResource.getURI()
										.toString());
		}

		IEntitiesRepresentationBuilder<?> rb = EntitiesRepresentationBuilderFactory
				.getRepresentationBuilder(requestedFormat, context);
		Document thumbRepresentation = (Document) rb.getThumbsRepresentation(
				uriInfo, metadataList, status);
		return Response.ok(thumbRepresentation).type(MediaType.APPLICATION_XML)
				.build();
	}

	private void checkFreshness(String providedEtag, String metadataId,
			String status) throws InvalidEtagException {
		CharSequence storedEtag = getThumbEtag(metadataId, status);
		if (!StringUtils.equals(providedEtag, (String) storedEtag))
			throw new InvalidEtagException(metadataId, providedEtag);

	}

	public static String getThumbEtag(String metadataId, String status) {
		return ResourceDirectoryInterface.getThumbEtag(ThumbsApi.getThumbId(
				metadataId, status));
	}

	public class ThumbDelayedChooserBuilder {
		private String metadataId;
		private int numberOfTries;
		private String etag;
		private String status;
		private boolean areMetadataIconsAccepted;

		public ThumbDelayedChooserBuilder metadata(String metadataId) {
			this.metadataId = metadataId;
			return this;
		}

		public ThumbDelayedChooserBuilder numberOfTries(int numberOfTries) {
			this.numberOfTries = numberOfTries;
			return this;
		}

		public ThumbDelayedChooserBuilder etag(String etag) {
			this.etag = etag;
			return this;
		}

		public ThumbDelayedChooserBuilder status(String status) {
			this.status = status;
			return this;
		}

		public ThumbDelayedChooserBuilder askForMetadataIcons(
				boolean askForMetadataIcons) {
			this.areMetadataIconsAccepted = askForMetadataIcons;
			return this;
		}

		public ThumbDelayedChooser build() {
			return new ThumbDelayedChooser(this);
		}

	}

	public class ThumbDelayedChooser implements Runnable {

		private final String metadataId;
		private final int numberOfTries;
		private final String etag;
		private final String status;
		private boolean askForMetadataIcons;

		public ThumbDelayedChooser(ThumbDelayedChooserBuilder builder) {
			this.metadataId = builder.metadataId;
			this.numberOfTries = builder.numberOfTries;
			this.etag = builder.etag;
			this.status = builder.status;
			this.askForMetadataIcons = builder.areMetadataIconsAccepted;
		}

		@Override
		public void run() {
			if (!automatedProcessesStop.get(metadataId))
				try {

					logger.info("Sheduled automated thumb choice N°"
							+ numberOfTries + " on " + maxNumberOfTries
							+ " for metadata " + metadataId + " : try number "
							+ numberOfTries);
					if (askForMetadataIcons)
						logger.info("Thumbs suggestions from Apiscol Meta will be accepted");
					checkFreshness(etag, metadataId, status);
					String url = selectMostAppropriateThumb(metadataId,
							askForMetadataIcons);
					try {
						if (StringUtils.isNotBlank(url))
							registerUrl(url, metadataId, status);
					} catch (InvalidImageUrlException e) {
						// no usable url
						logger.warn("The sheduled choice of thumbs was not able to handle this wrong url :"
								+ url + " for metadata " + metadataId);
						url = StringUtils.EMPTY;
					}
					// only the last attemp will ask for metadata icons
					if (StringUtils.isBlank(url)) {
						if (numberOfTries < maxNumberOfTries)
							delayedThumbChoiceExecutor
									.schedule(
											new ThumbDelayedChooserBuilder()
													.metadata(metadataId)
													.etag(etag)
													.numberOfTries(
															numberOfTries + 1)
													.status(status)
													.askForMetadataIcons(
															numberOfTries + 1 == maxNumberOfTries)
													.build(),
											automatedChoiceDelay
													* (numberOfTries + 1),
											TimeUnit.SECONDS);
					}
				} catch (InvalidEtagException e1) {
					logger.info("canceling sheduled automated thumb choice for metadata "
							+ metadataId + " : the etag is not old.");
				}
			else
				logger.info("canceling sheduled automated thumb choice for metadata "
						+ metadataId
						+ " : the metadata reference has been destroyed.");
		}
	}

	public static void stopExecutors() {
		if (logger != null)
			logger.info("Thread executors are going to be stopped for Apiscol Thumbs.");
		if (automatedProcessesStop != null)
			for (String key : automatedProcessesStop.keySet()) {
				automatedProcessesStop.put(key, false);
			}
		if (delayedThumbChoiceExecutor != null)
			delayedThumbChoiceExecutor.shutdown();
	}

}
