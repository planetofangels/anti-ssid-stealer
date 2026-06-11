package meow.meow.client;

import meow.meow.AntiSsid;
import meow.meow.client.security.TokenProtector;
import net.fabricmc.api.ClientModInitializer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class AntiSsidClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TokenProtector.enableAwtDialogs();
		registerSsidCommand();
	}

	private static void registerSsidCommand() {
		try {
			Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback");
			Object event = callbackClass.getField("EVENT").get(null);
			Object callback = Proxy.newProxyInstance(
					AntiSsidClient.class.getClassLoader(),
					new Class<?>[]{callbackClass},
					(proxy, method, args) -> {
						if (args != null && args.length > 0) {
							registerCommandNode(args[0]);
						}

						return null;
					}
			);

			event.getClass().getMethod("register", Object.class).invoke(event, callback);
		} catch (ReflectiveOperationException exception) {
			AntiSsid.LOGGER.warn("Failed to register /ssid command across this Minecraft version", exception);
		}
	}

	private static void registerCommandNode(Object dispatcher) throws ReflectiveOperationException {
		Class<?> commandManagerClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandManager");
		Class<?> commandClass = Class.forName("com.mojang.brigadier.Command");
		Object command = Proxy.newProxyInstance(
				AntiSsidClient.class.getClassLoader(),
				new Class<?>[]{commandClass},
				(proxy, method, args) -> method.getName().equals("run") ? executeSsidCommand() : null
		);

		Object literal = commandManagerClass.getMethod("literal", String.class).invoke(null, "ssid");
		Method executes = Arrays.stream(literal.getClass().getMethods())
				.filter(method -> method.getName().equals("executes"))
				.filter(method -> method.getParameterCount() == 1)
				.filter(method -> method.getParameterTypes()[0].isAssignableFrom(commandClass))
				.findFirst()
				.orElseThrow(NoSuchMethodException::new);
		Object executable = executes.invoke(literal, command);
		invokeFirst(dispatcher, executable, "register");
	}

	private static int executeSsidCommand() {
		try {
			Object client = getMinecraftClient();
			Object user = invokeFirst(client, "getUser", "method_1548");
			String accessToken = (String) invokeFirst(user, "getAccessToken", "method_1674");

			sendChatMessage(client, "sensitive info, minecraft access token");
			sendChatMessage(client, "Token: " + censorToken(accessToken));
		} catch (ReflectiveOperationException exception) {
			AntiSsid.LOGGER.warn("Failed to run /ssid command across this Minecraft version", exception);
		}

		return 1;
	}

	private static Object getMinecraftClient() throws ReflectiveOperationException {
		Class<?> minecraftClass = findClass("net.minecraft.client.Minecraft", "net.minecraft.class_310");
		return invokeFirstStatic(minecraftClass, "getInstance", "method_1551");
	}

	private static void sendChatMessage(Object client, String message) throws ReflectiveOperationException {
		Object component = createTextComponent(message);
		Object gui = getFieldValue(client, "gui", "field_1705");
		Object chat = invokeFirst(gui, "getChat", "method_1743");
		invokeFirst(chat, component, "addMessage", "method_1812");
	}

	private static Object createTextComponent(String message) throws ReflectiveOperationException {
		Class<?> componentClass = findClass("net.minecraft.network.chat.Component", "net.minecraft.class_2561");
		return Arrays.stream(componentClass.getMethods())
				.filter(method -> Modifier.isStatic(method.getModifiers()))
				.filter(method -> method.getReturnType().equals(componentClass))
				.filter(method -> method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(String.class))
				.filter(method -> method.getName().equals("literal") || method.getName().equals("method_43470"))
				.findFirst()
				.orElseThrow(NoSuchMethodException::new)
				.invoke(null, message);
	}

	private static Class<?> findClass(String... names) throws ClassNotFoundException {
		for (String name : names) {
			try {
				return Class.forName(name);
			} catch (ClassNotFoundException ignored) {
			}
		}

		throw new ClassNotFoundException(String.join(", ", names));
	}

	private static Object invokeFirstStatic(Class<?> owner, String... names) throws ReflectiveOperationException {
		for (String name : names) {
			try {
				Method method = owner.getMethod(name);
				return method.invoke(null);
			} catch (NoSuchMethodException ignored) {
			}
		}

		throw new NoSuchMethodException(owner.getName() + "#" + String.join("/", names));
	}

	private static Object invokeFirst(Object target, String... names) throws ReflectiveOperationException {
		return invokeFirst(target, null, names);
	}

	private static Object invokeFirst(Object target, Object argument, String... names) throws ReflectiveOperationException {
		for (String name : names) {
			for (Method method : target.getClass().getMethods()) {
				if (!method.getName().equals(name)) {
					continue;
				}

				if (argument == null && method.getParameterCount() == 0) {
					return method.invoke(target);
				}

				if (argument != null && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(argument)) {
					return method.invoke(target, argument);
				}
			}
		}

		throw new NoSuchMethodException(target.getClass().getName() + "#" + String.join("/", names));
	}

	private static Object getFieldValue(Object target, String... names) throws ReflectiveOperationException {
		for (String name : names) {
			try {
				Field field = target.getClass().getField(name);
				return field.get(target);
			} catch (NoSuchFieldException ignored) {
			}
		}

		throw new NoSuchFieldException(target.getClass().getName() + "#" + String.join("/", names));
	}

	private static String censorToken(String token) {
		if (token == null || token.isBlank()) {
			return "<unavailable>";
		}

		if (token.length() <= 10) {
			return "*".repeat(token.length());
		}

		return token.substring(0, 5) + "*".repeat(token.length() - 10) + token.substring(token.length() - 5);
	}
}
