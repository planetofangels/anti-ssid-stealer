package meow.meow.client.security;

import meow.meow.AntiSsid;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import java.nio.charset.StandardCharsets;
import java.awt.GraphicsEnvironment;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class TokenProtector {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final SecretKeySpec LAUNCH_KEY = createLaunchKey();
	private static final Set<String> LOGGED_CALLERS = ConcurrentHashMap.newKeySet();

	private TokenProtector() {
	}

	public static void enableAwtDialogs() {
		if ("true".equalsIgnoreCase(System.getProperty("java.awt.headless"))) {
			System.setProperty("java.awt.headless", "false");
			AntiSsid.LOGGER.info("enabled token warning dialogs");
		}
	}

	public static String protectAccessToken(String accessToken) {
		enableAwtDialogs();

		AccessRequest request = inspectAccessTokenRead();
		if (!isTrustedStartupRead(request.frames()) && !confirmAccessTokenRead(request.caller())) {
			AntiSsid.LOGGER.warn("Minecraft access token read blocked for {}", request.caller());
			return protectedPlaceholder("blocked");
		}

		if (isTrustedStartupRead(request.frames())) {
			logTrustedAccessTokenRead(request);
		}

		return encryptAccessToken(accessToken);
	}

	public static String decryptIfProtected(String token) {
		if (token == null || !token.startsWith("anti-ssid:v1:")) {
			return token;
		}

		try {
			byte[] payload = Base64.getUrlDecoder().decode(token.substring("anti-ssid:v1:".length()));
			if (payload.length <= 12) {
				return protectedPlaceholder("invalid");
			}

			byte[] nonce = new byte[12];
			byte[] encrypted = new byte[payload.length - nonce.length];
			System.arraycopy(payload, 0, nonce, 0, nonce.length);
			System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);

			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, LAUNCH_KEY, new GCMParameterSpec(128, nonce));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException | GeneralSecurityException exception) {
			AntiSsid.LOGGER.warn("Failed to decrypt protected access token", exception);
			return protectedPlaceholder("blocked");
		}
	}

	private static String encryptAccessToken(String accessToken) {
		if (accessToken == null || accessToken.isBlank()) {
			return protectedPlaceholder("unavailable");
		}

		byte[] nonce = new byte[12];
		RANDOM.nextBytes(nonce);

		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, LAUNCH_KEY, new GCMParameterSpec(128, nonce));
			byte[] encrypted = cipher.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[nonce.length + encrypted.length];
			System.arraycopy(nonce, 0, payload, 0, nonce.length);
			System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);

			return "anti-ssid:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
		} catch (GeneralSecurityException exception) {
			AntiSsid.LOGGER.warn("Failed to protect access token; returning blocked placeholder", exception);
			return protectedPlaceholder("blocked");
		}
	}

	private static String protectedPlaceholder(String reason) {
		return "anti-ssid:" + reason;
	}

	private static SecretKeySpec createLaunchKey() {
		byte[] key = new byte[32];
		RANDOM.nextBytes(key);
		return new SecretKeySpec(key, "AES");
	}

	private static AccessRequest inspectAccessTokenRead() {
		StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
		List<StackWalker.StackFrame> frames = walker.walk(stream -> stream
				.filter(frame -> !isInternalFrame(frame))
				.limit(16)
				.toList());
		String caller = frames.stream()
				.findFirst()
				.map(TokenProtector::formatFrame)
				.orElse("<unknown>");

		if (!LOGGED_CALLERS.add(caller)) {
			return new AccessRequest(caller, frames);
		}

		String stack = frames.stream()
				.map(frame -> "  at " + formatFrame(frame))
				.collect(Collectors.joining(System.lineSeparator()));

		AntiSsid.LOGGER.warn("Minecraft access token read intercepted from:{}{}", System.lineSeparator(), stack);
		return new AccessRequest(caller, frames);
	}

	private static void logTrustedAccessTokenRead(AccessRequest request) {
		String key = "trusted:" + request.caller();
		if (LOGGED_CALLERS.add(key)) {
			AntiSsid.LOGGER.info("Allowed trusted Minecraft access token read from {}", request.caller());
		}
	}

	private static boolean isTrustedStartupRead(List<StackWalker.StackFrame> frames) {
		return frames.stream().anyMatch(frame ->
				(frame.getClassName().equals("net.minecraft.client.Minecraft")
						|| frame.getClassName().equals("net.minecraft.class_310"))
						&& (frame.getMethodName().equals("<init>") || frame.getMethodName().equals("method_31382")));
	}

	private static String formatFrame(StackWalker.StackFrame frame) {
		return frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
	}

	private static boolean confirmAccessTokenRead(String caller) {
		if (GraphicsEnvironment.isHeadless()) {
			AntiSsid.LOGGER.warn("Cannot show confirmation dialog in a headless environment; blocking {}", caller);
			return false;
		}

		return "confirm".equalsIgnoreCase(showConfirmationDialog(caller));
	}

	private static String showConfirmationDialog(String caller) {
		AtomicReference<String> response = new AtomicReference<>("");
		Runnable prompt = () -> {
			JTextField input = new JTextField();
			Object[] message = {
					caller + " requested getAccessToken",
					"type confirm to continue",
					input
			};

			JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
			JDialog dialog = pane.createDialog(null, "anti-ssid token warning");
			dialog.setAlwaysOnTop(true);
			dialog.setModal(true);
			dialog.setVisible(true);
			dialog.dispose();

			Object value = pane.getValue();
			if (value instanceof Integer option && option == JOptionPane.OK_OPTION) {
				response.set(input.getText());
			}
		};

		try {
			if (javax.swing.SwingUtilities.isEventDispatchThread()) {
				prompt.run();
			} else {
				javax.swing.SwingUtilities.invokeAndWait(prompt);
			}
		} catch (Exception exception) {
			AntiSsid.LOGGER.warn("Failed to show access token confirmation dialog", exception);
			return "";
		}

		return response.get();
	}

	private static boolean isInternalFrame(StackWalker.StackFrame frame) {
		String className = frame.getClassName();
		String methodName = frame.getMethodName();

		return className.equals(TokenProtector.class.getName())
				|| className.equals("meow.meow.client.mixin.UserMixin")
				|| className.equals("meow.meow.client.mixin.UserNamedMixin")
				|| className.equals("net.minecraft.client.User")
				|| className.equals("net.minecraft.class_320")
				|| methodName.contains("antiSsid$protectAccessToken")
				|| className.startsWith("java.lang.Stack")
				|| className.startsWith("java.util.stream.");
	}

	private record AccessRequest(String caller, List<StackWalker.StackFrame> frames) {
	}
}
