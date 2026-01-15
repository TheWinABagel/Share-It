package com.bagel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.console.ConsoleModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.MessageUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class ShareCommand extends AbstractAsyncCommand {
    public ShareCommand() {
        super("share", "Share your held item to chat");
    }

    @Override
    protected @NonNull CompletableFuture<Void> executeAsync(@NonNull CommandContext commandContext) {
        CommandSender sender = commandContext.sender();
        if (sender instanceof Player player) {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                World world = store.getExternalData().getWorld();
                return CompletableFuture.runAsync(() -> {
                    PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
                    ItemStack stack = player.getInventory().getActiveHotbarItem();
                    int quantity = stack.getQuantity();
                    String message = "Sharing [%dx %s]".formatted(quantity, I18nModule.get().getMessage(playerRef.getLanguage(), stack.getItem().getTranslationKey()));
                    //TODO: Following is copy pasted from GamePacketHandler. Use that instead?
                    UUID playerUUID = playerRef.getUuid();
                    List<PlayerRef> targetPlayerRefs = new ObjectArrayList<>(Universe.get().getPlayers());
                    targetPlayerRefs.removeIf(targetPlayerRef -> targetPlayerRef.getHiddenPlayersManager().isPlayerHidden(playerUUID));
                    HytaleServer.get()
                            .getEventBus()
                            .dispatchForAsync(PlayerChatEvent.class)
                            .dispatch(new PlayerChatEvent(playerRef, targetPlayerRefs, message))
                            .whenComplete(
                                    (playerChatEvent, throwable) -> {
                                        if (throwable != null) {
                                            HytaleLogger.getLogger()
                                                    .at(Level.SEVERE)
                                                    .withCause(throwable)
                                                    .log("An error occurred while dispatching PlayerChatEvent for player %s", playerRef.getUsername());
                                        } else if (!playerChatEvent.isCancelled()) {
                                            Message sentMessage = playerChatEvent.getFormatter().format(playerRef, playerChatEvent.getContent());
                                            HytaleLogger.getLogger().at(Level.INFO).log(MessageUtil.toAnsiString(sentMessage).toAnsi(ConsoleModule.get().getTerminal()));

                                            for (PlayerRef targetPlayerRef : playerChatEvent.getTargets()) {
                                                targetPlayerRef.sendMessage(sentMessage);
                                            }
                                        }
                                    }
                            );
                    //End C&P
                }, world);
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
