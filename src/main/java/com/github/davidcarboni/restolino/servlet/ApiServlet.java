package com.github.davidcarboni.restolino.servlet;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.github.davidcarboni.restolino.Api;
import com.github.davidcarboni.restolino.reload.classes.ClassMonitor;

/**
 * This is the framework controller.
 * 
 * @author David Carboni
 * 
 */
public class ApiServlet extends HttpServlet {

	public static Api api;

	static final String KEY_CLASSES = "restolino.classes";

	/**
	 * Generated by Eclipse.
	 */
	private static final long serialVersionUID = 8375590483257379133L;

	@Override
	public void init() throws ServletException {

		// Start with the standard classloader:
		ClassLoader classLoader = Api.class.getClassLoader();

		String path = System.getProperty(KEY_CLASSES);
		if (StringUtils.isNotBlank(path)) {
			try {
				// Running with reloading,
				// so create a classloader that includes the classes URL:
				classLoader = new URLClassLoader(new URL[] { FileSystems
						.getDefault().getPath(path).toUri().toURL() },
						classLoader);
				// Monitor for changes - this will call setup():
				ClassMonitor.start(path, classLoader);
			} catch (IOException e) {
				throw new ServletException("Error starting class reloader", e);
			}
		} else {
			setup(classLoader);
		}
		System.out.println(this.getClass().getSimpleName() + " initialised.");
	}

	public static void setup(ClassLoader classLoader) throws ServletException {

		// api = new Api(classLoader);
		// api.setup(classLoader);
		try {

			Class<?> api = Class.forName(
					"com.github.davidcarboni.restolino.Api", true, classLoader);
			// Invoke the setup method:
			ApiServlet.api = (Api) api.getConstructor(ClassLoader.class)
					.newInstance(classLoader);
			// api.getMethod("setup", ClassLoader.class).invoke(api,
			// classLoader);
		} catch (InvocationTargetException | ClassNotFoundException
				| IllegalAccessException | IllegalArgumentException
				| NoSuchMethodException | SecurityException
				| InstantiationException e) {
			throw new ServletException("Error setting up API", e);
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) {
		System.out.println("API " + request.getMethod());
		api.get(request, response);
	}

	@Override
	public void doPut(HttpServletRequest request, HttpServletResponse response) {
		System.out.println(request.getMethod());
		api.put(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) {
		System.out.println(request.getMethod());
		api.post(request, response);
	}

	@Override
	public void doDelete(HttpServletRequest request,
			HttpServletResponse response) {
		System.out.println(request.getMethod());
		api.delete(request, response);
	}

	@Override
	public void doOptions(HttpServletRequest request,
			HttpServletResponse response) {
		System.out.println(request.getMethod());
		api.options(request, response);
	}

}
