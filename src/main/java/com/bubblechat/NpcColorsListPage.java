package com.bubblechat;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/** Page 1 of /bchat npc — the 5 nearest HyCitizens NPCs with compass direction + meters. */
public class NpcColorsListPage extends InteractiveCustomUIPage<NpcColorsListPage.PageData> {

    private static final double SEARCH_RADIUS = 64.0;
    private static final int MAX_NPCS = 5;

    private final SpeechManager manager;
    private final BubbleThemeStorage themeStorage;
    private final PlayerBubblePrefsStorage prefsStorage;
    private final NpcBubbleColorStorage npcColors;
    private final PlayerRef playerRef;
    private final UUID playerUuid;

    public NpcColorsListPage(@Nonnull SpeechManager manager,
                             @Nonnull BubbleThemeStorage themeStorage,
                             @Nonnull PlayerBubblePrefsStorage prefsStorage,
                             @Nonnull NpcBubbleColorStorage npcColors,
                             @Nonnull PlayerRef playerRef,
                             @Nonnull UUID playerUuid) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.manager = manager;
        this.themeStorage = themeStorage;
        this.prefsStorage = prefsStorage;
        this.npcColors = npcColors;
        this.playerRef = playerRef;
        this.playerUuid = playerUuid;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("NpcColorsList.ui");
        registerEvents(evt);
        buildList(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageData data) {
        if (data.selectId != null) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                String name = data.selectName != null ? data.selectName : data.selectId;
                player.getPageManager().openCustomPage(ref, store,
                    new NpcColorEditorPage(manager, themeStorage, prefsStorage, npcColors,
                        playerRef, playerUuid, data.selectId, name));
            }
            return;
        }

        if (data.action != null) {
            switch (data.action) {
                case "Refresh" -> refresh();
                case "Back" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.getPageManager().openCustomPage(ref, store,
                            new BubbleThemePage(manager, themeStorage, prefsStorage, playerRef, playerUuid));
                    }
                }
            }
        }
    }

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        buildList(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void registerEvents(@Nonnull UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            new EventData().append("Action", "Back"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
            new EventData().append("Action", "Refresh"), false);
    }

    private void buildList(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        cmd.clear("#ListCards");
        cmd.appendInline("#ListContainer", "Group #ListCards { LayoutMode: Top; }");

        List<HyCitizensNpcLookup.NearbyNpc> npcs = resolveNearby();
        if (npcs.isEmpty()) {
            cmd.appendInline("#ListCards",
                "Label { Text: \"No NPCs within 64m.\"; Style: (FontSize: 14, TextColor: #888888); Anchor: (Top: 10); }");
            return;
        }

        for (int i = 0; i < npcs.size(); i++) {
            HyCitizensNpcLookup.NearbyNpc n = npcs.get(i);
            cmd.append("#ListCards", "NpcColorEntry.ui");
            cmd.set("#ListCards[" + i + "] #EntryName.Text", n.name);
            cmd.set("#ListCards[" + i + "] #DirDist.Text", n.compass + " · " + Math.round(n.distXZ) + "m");

            NpcBubbleColorStorage.NpcColor c = npcColors.get(n.id);
            String swatch = (c != null && c.hex != null) ? c.hex + "FF" : "#33333380";
            cmd.set("#ListCards[" + i + "] #ColorSwatch.Background.Color", swatch);

            evt.addEventBinding(CustomUIEventBindingType.Activating,
                "#ListCards[" + i + "] #SelectButton",
                new EventData().append("SelectId", n.id).append("SelectName", n.name), false);
        }
    }

    private List<HyCitizensNpcLookup.NearbyNpc> resolveNearby() {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return List.of();
            Store<EntityStore> store = ref.getStore();
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) return List.of();
            return HyCitizensNpcLookup.nearest(tc.getPosition(), playerRef.getWorldUuid(), SEARCH_RADIUS, MAX_NPCS);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static class PageData {
        @Nonnull
        public static final BuilderCodec<PageData> CODEC = BuilderCodec
            .<PageData>builder(PageData.class, PageData::new)
            .addField(new KeyedCodec<>("Action", Codec.STRING),
                (d, v) -> d.action = v, d -> d.action)
            .addField(new KeyedCodec<>("SelectId", Codec.STRING),
                (d, v) -> d.selectId = v, d -> d.selectId)
            .addField(new KeyedCodec<>("SelectName", Codec.STRING),
                (d, v) -> d.selectName = v, d -> d.selectName)
            .build();

        String action;
        String selectId;
        String selectName;
    }
}
