package com.bubblechat;

import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reflection access to the HyCitizens citizen registry for the {@code /bchat npc} picker.
 * No compile-time or hard runtime dependency on HyCitizens — if it is not installed,
 * {@link #isAvailable()} is false and {@link #nearest} returns an empty list.
 *
 * <p>Compass convention matches PlayerRangeFinder: {@code atan2(dx, -dz)} with
 * {N,NE,E,SE,S,SW,W,NW}.</p>
 */
final class HyCitizensNpcLookup {

    /** One nearby NPC, with precomputed horizontal distance + absolute compass bearing. */
    static final class NearbyNpc {
        final String id;
        final String name;
        final Vector3d pos;
        final double distXZ;
        final String compass;
        NearbyNpc(String id, String name, Vector3d pos, double distXZ, String compass) {
            this.id = id; this.name = name; this.pos = pos; this.distXZ = distXZ; this.compass = compass;
        }
    }

    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static boolean initDone, available;
    private static Method getPlugin, getCitizensManager, getCitizensNear;
    private static Method getId, getName, getCurrentPosition, getWorldUUID;

    private HyCitizensNpcLookup() {}

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            Class<?> plugin = Class.forName("com.electro.hycitizens.HyCitizensPlugin");
            getPlugin = plugin.getMethod("get");
            getCitizensManager = plugin.getMethod("getCitizensManager");
            Class<?> mgr = Class.forName("com.electro.hycitizens.managers.CitizensManager");
            getCitizensNear = mgr.getMethod("getCitizensNear", Vector3d.class, double.class);
            Class<?> cd = Class.forName("com.electro.hycitizens.models.CitizenData");
            getId = cd.getMethod("getId");
            getName = cd.getMethod("getName");
            getCurrentPosition = cd.getMethod("getCurrentPosition");
            getWorldUUID = cd.getMethod("getWorldUUID");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    static boolean isAvailable() { init(); return available; }

    /** Nearest up-to-{@code limit} citizens in the player's world within {@code radius}, sorted by XZ distance. */
    static List<NearbyNpc> nearest(Vector3d playerPos, UUID worldUuid, double radius, int limit) {
        init();
        List<NearbyNpc> out = new ArrayList<>();
        if (!available || playerPos == null) return out;
        try {
            Object plugin = getPlugin.invoke(null);
            if (plugin == null) return out;
            Object mgr = getCitizensManager.invoke(plugin);
            if (mgr == null) return out;
            Object listObj = getCitizensNear.invoke(mgr, playerPos, radius);
            if (!(listObj instanceof List)) return out;
            for (Object cd : (List<?>) listObj) {
                if (cd == null) continue;
                Object w = getWorldUUID.invoke(cd);
                if (worldUuid != null && !worldUuid.equals(w)) continue;
                Object posObj = getCurrentPosition.invoke(cd);
                if (!(posObj instanceof Vector3d)) continue;
                Vector3d pos = (Vector3d) posObj;
                double dx = pos.x() - playerPos.x();
                double dz = pos.z() - playerPos.z();
                double distXZ = Math.sqrt(dx * dx + dz * dz);
                String id = String.valueOf(getId.invoke(cd));
                String name = String.valueOf(getName.invoke(cd));
                out.add(new NearbyNpc(id, name, pos, distXZ, compass(dx, dz)));
            }
            out.sort(Comparator.comparingDouble(n -> n.distXZ));
            if (out.size() > limit) out = new ArrayList<>(out.subList(0, limit));
        } catch (Throwable t) {
            // return whatever was gathered before the failure
        }
        return out;
    }

    /** Absolute compass bearing from a horizontal delta (matches PlayerRangeFinder). */
    static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        return DIRS[(int) Math.round(angle / 45.0) % 8];
    }
}
