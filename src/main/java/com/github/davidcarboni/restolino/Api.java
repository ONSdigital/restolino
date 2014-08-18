package com.github.davidcarboni.restolino;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.github.davidcarboni.restolino.helpers.Path;
import com.github.davidcarboni.restolino.interfaces.Boom;
import com.github.davidcarboni.restolino.interfaces.Endpoint;
import com.github.davidcarboni.restolino.interfaces.Home;
import com.github.davidcarboni.restolino.interfaces.NotFound;
import com.github.davidcarboni.restolino.json.Serialiser;
import com.github.davidcarboni.restolino.reload.classes.ClassMonitor;

/**
 * This is the framework controller.
 * 
 * @author David Carboni
 * 
 */
public class Api {

	static final String KEY_CLASSES = "restolino.classes";

	static RequestHandler defaultRequestHandler;
	static Map<String, RequestHandler> get;
	static Map<String, RequestHandler> put;
	static Map<String, RequestHandler> post;
	static Map<String, RequestHandler> delete;

	static Map<String, RequestHandler> getMap(Class<? extends Annotation> type) {
		if (GET.class.isAssignableFrom(type))
			return get;
		else if (PUT.class.isAssignableFrom(type))
			return put;
		else if (POST.class.isAssignableFrom(type))
			return post;
		else if (DELETE.class.isAssignableFrom(type))
			return delete;
		return null;
	}

	static Home home;
	static Boom boom;
	static NotFound notFound;

	static ClassLoader classLoader;

	public static void init() {

		// Start with the standard webapp classloader:
		ClassLoader classLoader = Api.class.getClassLoader();
		setup(classLoader);

		// If we're reloading classes, start the monitoring process:
		String path = System.getProperty(KEY_CLASSES);
		if (StringUtils.isNotBlank(path)) {
			try {
				ClassMonitor.start(path, classLoader);
			} catch (IOException e) {
				throw new RuntimeException("Error starting class reloader", e);
			}
		}
	}

	public static void setup(ClassLoader classLoader) {

		// Configuration
		ConfigurationBuilder configuration = new ConfigurationBuilder();
		// ArrayList<ClassLoader> classLoaders = new ArrayList<>();
		// ClassLoader c = classLoader;
		// do {
		// classLoaders.add(c);
		// c = c.getParent();
		// } while (c != null);
		// if (ClassMonitor.url != null)
		// configuration.setUrls(ClassMonitor.url);
		// configuration.setClassLoaders(classLoaders
		// .toArray(new ClassLoader[classLoaders.size()]));

		// System.out.println(" -> " + classLoader.getClass().getName());
		// System.out.println(" ---> "
		// + classLoader.getParent().getClass().getName());
		//
		// // Set up reflections:
		// ArrayList<URL> urls = new ArrayList<>();
		// ClassLoader parent = classLoader.getParent();
		// while (parent != null) {
		// System.out.println("Adding URLs from "
		// + parent.getClass().getName());
		// if (URLClassLoader.class.isAssignableFrom(parent.getClass())) {
		// for (URL url : ((URLClassLoader) parent).getURLs()) {
		// System.out.println(" - " + url);
		// if (!StringUtils.endsWith(url.toString(), "/classes/"))
		// urls.add(url);
		// else
		// System.out.println("   [skipped]");
		// }
		// }
		// parent = parent.getParent();
		// }
		Reflections reflections = new Reflections(
				ClasspathHelper.forClassLoader(classLoader));
		// System.out.println("Ref");
		// for (ClassLoader cl :
		// reflections.getConfiguration().getClassLoaders()) {
		// System.out.println(" r-> " + cl.getClass().getName());
		// }
		// ClasspathHelper.forClassLoader(classLoader));

		// Get the default handler:
		defaultRequestHandler = new RequestHandler();
		defaultRequestHandler.endpointClass = DefaultRequestHandler.class;
		try {
			defaultRequestHandler.method = DefaultRequestHandler.class
					.getMethod("notImplemented", HttpServletRequest.class,
							HttpServletResponse.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(
					"Code issue - default request handler not found", e);
		}

		configureEndpoints(reflections, classLoader);
		configureHome(reflections);
		configureNotFound(reflections);
		configureBoom(reflections);
	}

	/**
	 * Searches for and configures all your lovely endpoints.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	static void configureEndpoints(Reflections reflections,
			ClassLoader classLoader) {

		// [Re]initialise the maps:
		get = new HashMap<>();
		put = new HashMap<>();
		post = new HashMap<>();
		delete = new HashMap<>();

		System.out.println("Scanning for endpoints..");
		Set<Class<?>> endpoints = reflections
				.getTypesAnnotatedWith(Endpoint.class);

		System.out.println("Found " + endpoints.size() + " endpoints.");
		System.out.println("Examining endpoint methods..");

		// Configure the classes:
		for (Class<?> endpointClass : endpoints) {
			System.out.println(" - " + endpointClass.getSimpleName());
			String endpointName = StringUtils.lowerCase(endpointClass
					.getSimpleName());
			System.out.println(" -> "
					+ endpointClass.getClassLoader().getClass().getName());
			if (endpointClass.getClassLoader().getParent() != null)
				System.out.println(" ---> "
						+ endpointClass.getClassLoader().getParent().getClass()
								.getName());
			for (URL url : ((URLClassLoader) endpointClass.getClassLoader())
					.getURLs()) {
				System.out.println(" * " + url);
			}
			try {
				endpointClass = Class.forName(endpointClass.getName(), true,
						classLoader);
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
			for (Method method : endpointClass.getMethods()) {

				// Skip Object methods
				if (method.getDeclaringClass() == Object.class)
					continue;

				// We're looking for public methods that take reqest, responso
				// and optionally a message type:
				Class<?>[] parameterTypes = method.getParameterTypes();
				// System.out.println("Examining method " + method.getName());
				// if (Modifier.isPublic(method.getModifiers()))
				// System.out.println(".public");
				// System.out.println("." + parameterTypes.length +
				// " parameters");
				// if (parameterTypes.length == 2 || parameterTypes.length == 3)
				// {
				// if (HttpServletRequest.class
				// .isAssignableFrom(parameterTypes[0]))
				// System.out.println(".request OK");
				// if (HttpServletResponse.class
				// .isAssignableFrom(parameterTypes[1]))
				// System.out.println(".response OK");
				// }
				if (Modifier.isPublic(method.getModifiers())
						&& parameterTypes.length >= 2
						&& HttpServletRequest.class
								.isAssignableFrom(parameterTypes[0])
						&& HttpServletResponse.class
								.isAssignableFrom(parameterTypes[1])) {

					// Which HTTP method(s) will this method respond to?
					List<Annotation> annotations = Arrays.asList(method
							.getAnnotations());
					// System.out.println("    > processing " +
					// method.getName());
					// for (Annotation annotation : annotations)
					// System.out.println("    >   annotation " +
					// annotation.getClass().getName());
					for (Annotation annotation : annotations) {

						Map<String, RequestHandler> map = getMap(annotation
								.getClass());
						if (map != null) {
							clashCheck(endpointName, annotation.getClass(),
									endpointClass, method);
							System.out.print("   - "
									+ annotation.getClass().getInterfaces()[0]
											.getSimpleName());
							RequestHandler requestHandler = new RequestHandler();
							requestHandler.endpointClass = endpointClass;
							requestHandler.method = method;
							System.out.print(" " + method.getName());
							if (parameterTypes.length > 2) {
								requestHandler.requestMessageType = parameterTypes[2];
								System.out.print(" request:"
										+ requestHandler.requestMessageType
												.getSimpleName());
							}
							if (method.getReturnType() != void.class) {
								requestHandler.responseMessageType = method
										.getReturnType();
								System.out.print(" response:"
										+ requestHandler.responseMessageType
												.getSimpleName());
							}
							map.put(endpointName, requestHandler);
							System.out.println();
						}
					}
				}
			}

			// Set default handlers where needed:
			if (!get.containsKey(endpointName))
				get.put(endpointName, defaultRequestHandler);
			if (!put.containsKey(endpointName))
				put.put(endpointName, defaultRequestHandler);
			if (!post.containsKey(endpointName))
				post.put(endpointName, defaultRequestHandler);
			if (!delete.containsKey(endpointName))
				delete.put(endpointName, defaultRequestHandler);
		}

	}

	private static void clashCheck(String name,
			Class<? extends Annotation> annotation, Class<?> endpointClass,
			Method method) {
		Map<String, RequestHandler> map = getMap(annotation);
		if (map != null) {
			if (map.containsKey(name))
				System.out.println("   ! method " + method.getName() + " in "
						+ endpointClass.getName() + " overwrites "
						+ map.get(name).method.getName() + " in "
						+ map.get(name).endpointClass.getName() + " for "
						+ annotation.getSimpleName());
		} else {
			System.out.println("WAT. Expected GET/PUT/POST/DELETE but got "
					+ annotation.getName());
		}
	}

	/**
	 * Searches for and configures the / endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	static void configureHome(Reflections reflections) {

		System.out.println("Checking for a / endpoint..");
		home = getEndpoint(Home.class, "/", reflections);
		if (home != null)
			System.out.println("Class " + home.getClass().getSimpleName()
					+ " configured as / endpoint");
	}

	/**
	 * Searches for and configures the not found endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	static void configureNotFound(Reflections reflections) {

		System.out.println("Checking for a not-found endpoint..");
		notFound = getEndpoint(NotFound.class, "not-found", reflections);
		if (notFound != null)
			System.out.println("Class " + notFound.getClass().getSimpleName()
					+ " configured as not-found endpoint");
	}

	/**
	 * Searches for and configures the not found endpoint.
	 * 
	 * @param reflections
	 *            The instance to use to find classes.
	 */
	static void configureBoom(Reflections reflections) {

		System.out.println("Checking for an error endpoint..");
		boom = getEndpoint(Boom.class, "error", reflections);
		if (boom != null)
			System.out.println("Class " + boom.getClass().getSimpleName()
					+ " configured as error endpoint");
	}

	/**
	 * Locates a single endpoint class.
	 * 
	 * @param type
	 * @param name
	 * @param reflections
	 * @return
	 */
	private static <E> E getEndpoint(Class<E> type, String name,
			Reflections reflections) {
		E result = null;

		// Get annotated classes:
		Set<Class<? extends E>> endpointClasses = reflections
				.getSubTypesOf(type);

		if (endpointClasses.size() == 0)

			// No endpoint found:
			System.out.println("No " + name
					+ " endpoint configured. Just letting you know.");

		else {

			// Dump multiple endpoints:
			if (endpointClasses.size() > 1) {
				System.out.println("Warning: found multiple candidates for "
						+ name + " endpoint: " + endpointClasses);
			}

			// Instantiate the endpoint:
			try {
				result = endpointClasses.iterator().next().newInstance();
			} catch (Exception e) {
				System.out.println("Error: cannot instantiate " + name
						+ " endpoint class "
						+ endpointClasses.iterator().next());
				e.printStackTrace();
			}
		}

		return result;
	}

	public static void doGet(HttpServletRequest request,
			HttpServletResponse response) {

		if (home != null && isRootRequest(request)) {
			// Handle a / request:
			Object responseMessage = home.get(request, response);
			if (responseMessage != null)
				writeMessage(response, responseMessage.getClass(),
						responseMessage);
		} else {
			doMethod(request, response, get);
		}
	}

	public static void doPut(HttpServletRequest request,
			HttpServletResponse response) {
		doMethod(request, response, put);
	}

	public static void doPost(HttpServletRequest request,
			HttpServletResponse response) {
		doMethod(request, response, post);
	}

	public static void doDelete(HttpServletRequest request,
			HttpServletResponse response) {
		doMethod(request, response, delete);
	}

	public static void doOptions(HttpServletRequest request,
			HttpServletResponse response) {

		List<String> result = new ArrayList<>();

		if (home != null && isRootRequest(request)) {

			// We only allow GET to the root resource:
			result.add("GET");

		} else {

			// Determine which methods are configured:
			if (mapRequestPath(get, request) != null)
				result.add("GET");
			if (mapRequestPath(put, request) != null)
				result.add("PUT");
			if (mapRequestPath(post, request) != null)
				result.add("POST");
			if (mapRequestPath(delete, request) != null)
				result.add("DELETE");
		}

		response.setHeader("Allow", StringUtils.join(result, ','));
		// writeMessage(response, List.class, result);
	}

	/**
	 * Determines if the given request is for the root resource (ie /).
	 * 
	 * @param request
	 *            The {@link HttpServletRequest}
	 * @return If {@link HttpServletRequest#getPathInfo()} is null, empty string
	 *         or "/" ten true.
	 */
	private static boolean isRootRequest(HttpServletRequest request) {
		String path = request.getPathInfo();
		if (StringUtils.isBlank(path))
			return true;
		if (StringUtils.equals("/", path))
			return true;
		return false;
	}

	/**
	 * GO!
	 * 
	 * @param request
	 *            The request.
	 * @param response
	 *            The response.
	 * @param requestHandlers
	 *            One of the handler maps.
	 */
	static void doMethod(HttpServletRequest request,
			HttpServletResponse response,
			Map<String, RequestHandler> requestHandlers) {

		// Locate a request handler:
		RequestHandler requestHandler = mapRequestPath(requestHandlers, request);

		try {

			if (requestHandler != null) {

				Object handler = instantiate(requestHandler.endpointClass);
				Object responseMessage = invoke(request, response, handler,
						requestHandler.method,
						requestHandler.requestMessageType);
				if (requestHandler.responseMessageType != null) {
					writeMessage(response, requestHandler.responseMessageType,
							responseMessage);
				}

			} else {

				// Not found
				response.setStatus(HttpStatus.SC_NOT_FOUND);
				if (notFound != null)
					notFound.handle(request, response);
			}
		} catch (Throwable t) {

			// Set a default response code:
			response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);

			if (boom != null) {
				try {
					// Attempt to handle the error gracefully:
					boom.handle(request, response, requestHandler, t);
				} catch (Throwable t2) {
					t2.printStackTrace();
				}
			} else {
				t.printStackTrace();
			}
		}

	}

	private static Object invoke(HttpServletRequest request,
			HttpServletResponse response, Object handler, Method method,
			Class<?> requestMessage) {
		Object result = null;

		System.out.println("Invoking method " + method.getName() + " on "
				+ handler.getClass().getSimpleName() + " for request message "
				+ requestMessage);
		try {
			if (requestMessage != null) {
				Object message = readMessage(request, requestMessage);
				result = method.invoke(handler, request, response, message);
			} else {
				result = method.invoke(handler, request, response);
			}
		} catch (Exception e) {
			System.out.println("!Error: " + e.getMessage());
			throw new RuntimeException("Error invoking method "
					+ method.getName() + " on "
					+ handler.getClass().getSimpleName(), e);
		}

		System.out.println("Result is " + result);
		return result;
	}

	private static Object readMessage(HttpServletRequest request,
			Class<?> requestMessageType) {

		try (InputStreamReader streamReader = new InputStreamReader(
				request.getInputStream(), "UTF8")) {
			return Serialiser.getBuilder().create()
					.fromJson(streamReader, requestMessageType);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Unsupported encoding", e);
		} catch (IOException e) {
			throw new RuntimeException("Error reading message", e);
		}
	}

	private static void writeMessage(HttpServletResponse response,
			Class<?> responseMessageType, Object responseMessage) {

		if (responseMessage != null) {

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF8");
			try (OutputStreamWriter writer = new OutputStreamWriter(
					response.getOutputStream(), "UTF8")) {

				Serialiser.getBuilder().create()
						.toJson(responseMessage, responseMessageType, writer);
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("Unsupported encoding", e);
			} catch (IOException e) {
				throw new RuntimeException("Error reading message", e);
			}
		}
	}

	/**
	 * Locates a {@link RequestHandler} for the path of the given request.
	 * 
	 * @param requestHandlers
	 *            One of the handler maps.
	 * @param request
	 *            The request.
	 * @return A matching handler, if one exists.
	 */
	private static RequestHandler mapRequestPath(
			Map<String, RequestHandler> requestHandlers,
			HttpServletRequest request) {

		String endpointName = Path.newInstance(request).firstSegment();
		endpointName = StringUtils.lowerCase(endpointName);
		// System.out.println("Mapping endpoint " + endpointName);
		return requestHandlers.get(endpointName);
	}

	private static Object instantiate(Class<?> endpointClass) {

		// Instantiate:
		Object result = null;
		try {
			result = endpointClass.newInstance();
		} catch (InstantiationException e) {
			throw new RuntimeException("Unable to instantiate "
					+ endpointClass.getSimpleName(), e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Unable to access "
					+ endpointClass.getSimpleName(), e);
		} catch (NullPointerException e) {
			throw new RuntimeException("No class to instantiate", e);
		}
		return result;

	}

}
