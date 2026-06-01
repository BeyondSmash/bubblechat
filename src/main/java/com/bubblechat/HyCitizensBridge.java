package com.bubblechat;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Optional integration: shows a BubbleChat speech bubble above a HyCitizens NPC
 * when a player interacts with it.
 *
 * <p>Entirely reflection-based — BubbleChat has NO compile-time or runtime hard
 * dependency on HyCitizens. If HyCitizens is not installed, this silently does
 * nothing. Targets the HyCitizens 1.7.x developer API:
 * {@code CitizensManager.addCitizenInteractListener(CitizenInteractListener)}.</p>
 */
final class HyCitizensBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000;

    private HyCitizensBridge() {}

    /** Attempt to register the NPC-interact listener, retrying while HyCitizens finishes loading. */
    static void register(SpeechManager manager) {
        attempt(manager, 1);
    }

    private static void attempt(SpeechManager manager, int attempt) {
        final Class<?> pluginClass;
        try {
            pluginClass = Class.forName("com.electro.hycitizens.HyCitizensPlugin");
        } catch (ClassNotFoundException notInstalled) {
            // HyCitizens not present — integration simply stays off.
            return;
        }

        try {
            Object plugin = pluginClass.getMethod("get").invoke(null);
            if (plugin == null) {
                // HyCitizens class is loaded but the plugin hasn't started yet — retry.
                if (attempt < MAX_ATTEMPTS) {
                    manager.getScheduler().schedule(() -> attempt(manager, attempt + 1),
                        RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                } else {
                    LOGGER.atWarning().log("HyCitizens detected but never became ready; NPC bubbles disabled.");
                }
                return;
            }

            Object citizensManager = pluginClass.getMethod("getCitizensManager").invoke(plugin);
            if (citizensManager == null) {
                if (attempt < MAX_ATTEMPTS) {
                    manager.getScheduler().schedule(() -> attempt(manager, attempt + 1),
                        RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                }
                return;
            }

            Class<?> listenerIface = Class.forName("com.electro.hycitizens.events.CitizenInteractListener");
            Object listener = buildListenerProxy(manager, listenerIface);

            citizensManager.getClass()
                .getMethod("addCitizenInteractListener", listenerIface)
                .invoke(citizensManager, listener);

            LOGGER.atInfo().log("HyCitizens integration enabled — NPC speech bubbles active.");
        } catch (Throwable t) {
            LOGGER.atWarning().log("Failed to enable HyCitizens integration: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object buildListenerProxy(SpeechManager manager, Class<?> listenerIface) throws Exception {
        // Cache the reflective accessors once.
        final Class<?> eventClass = Class.forName("com.electro.hycitizens.events.CitizenInteractEvent");
        final Method getCitizen = eventClass.getMethod("getCitizen");

        final Class<?> citizenDataClass = Class.forName("com.electro.hycitizens.models.CitizenData");
        final Method getNpcRef = citizenDataClass.getMethod("getNpcRef");
        final Method getWorldUUID = citizenDataClass.getMethod("getWorldUUID");
        final Method getId = citizenDataClass.getMethod("getId");
        final Method getMessagesConfig = citizenDataClass.getMethod("getMessagesConfig");

        final Class<?> messagesConfigClass = Class.forName("com.electro.hycitizens.models.MessagesConfig");
        final Method getMessages = messagesConfigClass.getMethod("getMessages");

        final Class<?> citizenMessageClass = Class.forName("com.electro.hycitizens.models.CitizenMessage");
        final Method getMessage = citizenMessageClass.getMethod("getMessage");

        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "BubbleChatHyCitizensListener";
                default: break;
            }
            // onCitizenInteract(CitizenInteractEvent)
            try {
                if (args == null || args.length == 0 || args[0] == null) return null;
                Object event = args[0];
                Object citizen = getCitizen.invoke(event);
                if (citizen == null) return null;

                Object refObj = getNpcRef.invoke(citizen);
                if (!(refObj instanceof Ref)) return null;
                @SuppressWarnings("unchecked")
                Ref<EntityStore> npcRef = (Ref<EntityStore>) refObj;

                Object worldObj = getWorldUUID.invoke(citizen);
                UUID worldUuid = (worldObj instanceof UUID) ? (UUID) worldObj : null;
                Object idObj = getId.invoke(citizen);
                String npcId = (idObj != null) ? String.valueOf(idObj) : null;

                String text = resolveMessage(citizen, getMessagesConfig, getMessages, getMessage);
                if (text == null || text.isBlank()) return null;

                manager.showEntityBubble(npcRef, worldUuid, npcId, text);
            } catch (Throwable t) {
                LOGGER.atWarning().log("HyCitizens interact handler error: " + t.getMessage());
            }
            return null;
        };

        return Proxy.newProxyInstance(listenerIface.getClassLoader(),
            new Class<?>[]{listenerIface}, handler);
    }

    /** Pick the first non-blank configured message for the citizen (v1: simplest selection). */
    private static String resolveMessage(Object citizen, Method getMessagesConfig,
                                         Method getMessages, Method getMessage) throws Exception {
        Object config = getMessagesConfig.invoke(citizen);
        if (config == null) return null;
        Object listObj = getMessages.invoke(config);
        if (!(listObj instanceof List)) return null;
        List<?> messages = (List<?>) listObj;
        for (Object m : messages) {
            if (m == null) continue;
            Object text = getMessage.invoke(m);
            if (text instanceof String && !((String) text).isBlank()) {
                return (String) text;
            }
        }
        return null;
    }
}
