package fr.ac_versailles.crdp.apiscol.thumbs;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.core.Application;

import com.sun.jersey.spi.container.servlet.ServletContainer;

public class ApiscolThumbs extends ServletContainer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public ApiscolThumbs() {

	}

	public ApiscolThumbs(Class<? extends Application> appClass) {
		super(appClass);
	}

	public ApiscolThumbs(Application app) {
		super(app);
	}

	@PreDestroy
	public void deinitialize() {
		ThumbsApi.stopExecutors();
	}

	@PostConstruct
	public void initialize() {
		// nothing at this time
	}
}
