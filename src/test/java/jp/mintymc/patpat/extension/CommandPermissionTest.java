package jp.mintymc.patpat.extension;

import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.extension.command.GeyserExtensionCommand;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.platform.velocity.shaded.org.incendo.cloud.CommandManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Properties;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandPermissionTest {
    @SuppressWarnings("unchecked")
    @Test void actualGeyserCommandPredicatesAllowPublicCommandsWithoutProxyNodes() {
        PatPatExtension extension=mock(PatPatExtension.class,CALLS_REAL_METHODS);
        doReturn("patpatbedrock").when(extension).rootCommand();
        var event=mock(GeyserDefineCommandsEvent.class);
        // Use the actual pinned Geyser builder, with only its global API factory replaced.
        try(var factory=mockStatic(Command.class)) {
            factory.when(()->Command.builder(extension)).thenAnswer(call->new GeyserExtensionCommand.Builder<CommandSource>(extension));
            extension.commands(event);
        }
        ArgumentCaptor<Command> commands=ArgumentCaptor.forClass(Command.class);
        verify(event,times(5)).register(commands.capture());
        CommandManager<GeyserCommandSource> manager=mock(CommandManager.class);
        GeyserCommandSource source=mock(GeyserCommandSource.class);
        when(source.connection()).thenReturn(mock(GeyserSession.class));
        for(Command command:commands.getAllValues()) {
            GeyserCommand concrete=(GeyserCommand)command;
            boolean allowed=concrete.commandPermission(manager).testPermission(source).allowed();
            if(command.name().equals("reload"))assertFalse(allowed,"Admin command must stay protected");
            else {assertEquals("",command.permission());assertTrue(allowed,command.name());}
        }
    }
    @Test void defaultPolicyDoesNotRequirePermissionPlugin() {
        CommandSource source=mock(CommandSource.class);
        assertTrue(Settings.from(new Properties()).allows(source));
        verifyNoInteractions(source);
    }
    @Test void explicitConfiguredPermissionStillRestrictsUse() {
        Properties props=new Properties();props.setProperty("required-permission","patpatbedrock.use");
        Settings settings=Settings.from(props);CommandSource source=mock(CommandSource.class);
        assertFalse(settings.allows(source));
        when(source.hasPermission("patpatbedrock.use")).thenReturn(true);
        assertTrue(settings.allows(source));
    }
}
