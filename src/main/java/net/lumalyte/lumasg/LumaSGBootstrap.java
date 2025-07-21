package net.lumalyte;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.lumalyte.commands.SGCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class LumaSGBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Register commands using the LifecycleEventManager
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            // Create the command instance (plugin will be null during bootstrap, but that's handled)
            SGCommand sgCommand = new SGCommand(null);
            
            // Register the command using Paper's Commands registrar
            // This follows the recommended pattern from Paper's documentation
            event.registrar().register(sgCommand.createCommandNode(), "Main LumaSG command for managing survival games");
        });
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new LumaSG();
    }
} 