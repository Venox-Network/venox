package network.venox.cobalt.commands;

import com.freya02.botcommands.api.annotations.CommandMarker;
import com.freya02.botcommands.api.annotations.Dependency;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.freya02.botcommands.api.components.Components;
import com.freya02.botcommands.api.components.event.ButtonEvent;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import network.venox.cobalt.Cobalt;
import network.venox.cobalt.data.objects.CoEmbed;
import network.venox.cobalt.data.objects.CoSuperBan;
import network.venox.cobalt.utility.CoUtilities;
import network.venox.cobalt.utility.DurationParser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;


@CommandMarker
public class SuperCmd extends ApplicationCommand {
    @NotNull private static final String AC_BAN_USER = "SuperCmd.banCommand.user";
    @NotNull private static final String AC_UNBAN_USER = "SuperCmd.unbanCommand.user";

    @Dependency private Cobalt cobalt;

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "super",
            subcommand = "ban",
            description = "Bans the specified user from all Venox servers")
    public void banCommand(@NotNull GlobalSlashEvent event,
                          @AppOption(description = "The ID of the user to ban", autocomplete = AC_BAN_USER) @NotNull String user,
                          @AppOption(description = "The reason for the ban") @NotNull String reason,
                          @AppOption(description = "Duration of the ban. If empty, ban is permanent") @Nullable String duration) {
        final User userJda = CoUtilities.getUser(event, user);
        if (userJda == null || !cobalt.config.checkIsOwner(event)) return;

        // Check if user is already banned
        final CoSuperBan current = cobalt.data.global.getSuperBan(userJda.getIdLong());
        if (current != null) {
            if (!current.isExpired()) {
                final User currentMod = current.getModerator();
                if (currentMod != null) event.replyEmbeds(cobalt.messages.getEmbed("command", "super", "ban", "already")
                                .replace("%username%", userJda.getName())
                                .replace("%mention%", userJda.getAsMention())
                                .replace("%reason%", current.reason())
                                .replace("%timeleft%", current.getTimeLeft())
                                .replace("%moderator%", currentMod.getAsMention())
                                .build())
                        .setEphemeral(true).queue();
                return;
            }
            cobalt.data.global.superBans.remove(current);
        }

        // duration
        Long durationLong = null;
        final String durationString = duration == null ? "Permanent" : duration;
        if (duration != null) {
            final Duration newDuration = DurationParser.parse(durationString);
            if (newDuration == null) {
                event.replyEmbeds(CoEmbed.invalidArgument(durationString).build()).setEphemeral(true).queue();
                return;
            }
            durationLong = System.currentTimeMillis() + newDuration.toMillis();
        }

        // Buttons
        final Long finalDurationLong = durationLong;
        final Button yesButton = Components.successButton(buttonEvent -> ban(buttonEvent, user, reason, finalDurationLong, durationString)).build("Yes");
        final Button noButton = Components.dangerButton(buttonEvent -> buttonEvent.editMessage("Cancelled!").queue(message -> message.editOriginalComponents(List.of()).queue())).build("No");

        // Confirmation message
        event.reply("Are you sure you want to **superban** " + userJda.getAsMention() + "?\nThis will ban them from **all** Venox Network servers!")
                .addActionRow(yesButton, noButton)
                .setEphemeral(true).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "super",
            subcommand = "kick",
            description = "Kicks the specified user from all Venox servers")
    public void kickCommand(@NotNull GlobalSlashEvent event,
                          @AppOption(description = "The ID of the user to kick") @NotNull String user,
                          @AppOption(description = "The reason for the kick") @NotNull String reason) {
        final User userJda = CoUtilities.getUser(event, user);
        if (userJda == null || !cobalt.config.checkIsOwner(event)) return;

        // Send message to user
        userJda.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage("You have been kicked from all Venox Network servers for the following reason:\n> " + reason))
                .queue(s -> {}, f -> {});

        // Kick user from all guilds
        for (final Guild guild : event.getJDA().getGuilds()) guild.kick(userJda).reason(reason).queue(s -> {}, f -> {});

        // Reply
        event.reply("Kicked " + userJda.getAsMention() + " from all Venox Network servers").setEphemeral(true).queue();

        // Log
        cobalt.config.sendLog("superkick", "**User:** " + userJda.getAsMention() + "\n**Reason:** " + reason + "\n**Moderator:** " + event.getUser().getAsMention());
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "super",
            subcommand = "unban",
            description = "Unbans the specified user from all Venox servers")
    public void unbanCommand(@NotNull GlobalSlashEvent event,
                          @AppOption(description = "The ID of the user to unban", autocomplete = AC_UNBAN_USER) @NotNull String user) {
        final User userJda = CoUtilities.getUser(event, user);
        if (userJda == null || !cobalt.config.checkIsOwner(event)) return;

        // Get ban
        final CoSuperBan ban = cobalt.data.global.getSuperBan(userJda.getIdLong());
        if (ban == null) {
            event.reply(userJda.getAsMention() + " is not super-banned!").setEphemeral(true).queue();
            return;
        }

        // Unban user
        cobalt.data.global.superBans.remove(ban);
        ban.unban();

        // Reply
        event.reply(userJda.getAsMention() + " has been unbanned from all Venox servers").setEphemeral(true).queue();
    }

    @AutocompletionHandler(name = AC_BAN_USER) @NotNull
    public List<Command.Choice> acBanUser(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!cobalt.config.isOwner(event.getUser())) return List.of();
        return CoUtilities.acGuildMembers(event);
    }

    @AutocompletionHandler(name = AC_UNBAN_USER) @NotNull
    public List<Command.Choice> acUnbanUser(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!cobalt.config.isOwner(event.getUser())) return List.of();
        return cobalt.data.global.superBans.stream()
                .map(ban -> {
                    final User user = ban.getUser();
                    if (user == null) return null;
                    return new Command.Choice(user.getAsTag(), user.getIdLong());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private void ban(@NotNull ButtonEvent event, @NotNull String user, @NotNull String reason, @Nullable Long duration, @NotNull String durationString) {
        final JDA jda = event.getJDA();

        // Get User
        final User userJda = jda.getUserById(user);
        if (userJda == null) return;

        // Add ban
        final User moderator = event.getUser();
        final CoSuperBan superBan = new CoSuperBan(jda, userJda.getIdLong(), reason, duration, moderator.getIdLong());
        cobalt.data.global.superBans.add(superBan);

        // Send message to moderator
        event.editMessageEmbeds(cobalt.messages.getEmbed("command", "super", "ban", "success")
                        .replace("%username%", userJda.getName())
                        .replace("%mention%", userJda.getAsMention())
                        .replace("%reason%", reason)
                        .replace("%duration%", durationString)
                        .build())
                .setContent("")
                .setComponents(List.of())
                .queue();

        // Send message to user
        userJda.openPrivateChannel()
                .flatMap(channel -> channel.sendMessageEmbeds(cobalt.messages.getEmbed("command", "super", "ban", "user")
                        .replace("%reason%", reason)
                        .replace("%duration%", durationString)
                        .replace("%moderator%", moderator.getAsMention())
                        .replace("%servers%", "`" + jda.getGuilds().stream()
                                .map(Guild::getName)
                                .reduce((s, s2) -> s + "`, `" + s2) + "`")
                        .build()))
                .queue(s -> {}, f -> {});

        // Send log
        cobalt.config.sendLog("superban", "**User:** " + userJda.getAsMention() + "\n**Reason:** " + reason + "\n**Duration:** " + durationString + "\n**Moderator:** " + moderator.getAsMention());

        // Ban user
        superBan.ban();
    }
}
