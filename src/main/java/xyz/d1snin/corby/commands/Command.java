/*                          GNU GENERAL PUBLIC LICENSE
 *                            Version 3, 29 June 2007
 *
 *        Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 *            Everyone is permitted to copy and distribute verbatim copies
 *             of this license document, but changing it is not allowed.
 */

package xyz.d1snin.corby.commands;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import xyz.d1snin.corby.Corby;
import xyz.d1snin.corby.database.managers.PrefixManager;
import xyz.d1snin.corby.enums.Category;
import xyz.d1snin.corby.enums.EmbedTemplate;
import xyz.d1snin.corby.manager.CooldownsManager;
import xyz.d1snin.corby.model.Cooldown;
import xyz.d1snin.corby.utils.Embeds;
import xyz.d1snin.corby.utils.ExceptionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class Command extends ListenerAdapter {

  private static final List<Command> commands = new ArrayList<>();
  @Getter protected String alias = null;
  @Getter protected String description = null;
  @Getter protected Category category = null;
  @Getter protected String[] usages = null;
  @Getter @Setter protected int cooldown = 0;
  @Getter protected String longDescription = null;
  @Getter protected Permission[] permissions = new Permission[0];
  @Getter protected Permission[] botPermissions = new Permission[0];
  private MessageReceivedEvent event = null;

  public static List<Command> getCommandsByCategory(Category category) {
    List<Command> result = new ArrayList<>();
    for (Command c : getCommands()) {
      if (c.getCategory() == category) {
        result.add(c);
      }
    }
    return result;
  }

  public static Command getCommandByAlias(String alias) {
    for (Command c : getCommands()) {
      if (c.getAlias().equals(alias)) {
        return c;
      }
    }
    return null;
  }

  public static Command add(Command command) {

    final String invalidCommandConfig =
        "It looks like one of the fields is not initialized in the command (%s), fields alias, description, category and usages should be initialized. This command are ignored.";

    if (command.getAlias() == null
        || command.getDescription() == null
        || command.getCategory() == null
        || command.getUsages() == null) {
      Corby.log.warn(String.format(invalidCommandConfig, command.getClass().getName()));
    } else {
      commands.add(command);
    }
    Corby.permissions.addAll(Arrays.asList(command.getBotPermissions()));

    command.setCooldown(
        command.getCooldown() == 0 ? Corby.config.getDefaultCooldown() : command.getCooldown());
    return command;
  }

  public static List<Command> getCommands() {
    return commands;
  }

  protected abstract void execute(MessageReceivedEvent e, String[] args) throws IOException;

  protected abstract boolean isValidSyntax(MessageReceivedEvent e, String[] args);

  public String getUsagesString() {
    StringBuilder sb = new StringBuilder();
    for (String s : getUsages()) {
      sb.append(String.format(s, PrefixManager.getPrefix(event.getGuild()))).append("\n");
    }
    return sb.toString();
  }

  public void onMessageReceived(@NotNull MessageReceivedEvent e) {
    event = e;

    final String invalidPermission = "You must have permissions %s to use this command.";
    final String invalidBotPermission =
        "It looks like I do not have or I do not have enough permissions on this server, please invite me using [this](%s) link, I am leaving right now.";
    final String invalidSyntax = "**Incorrect Syntax:** `%s`\n\n**Usage:**\n%s";
    final String cooldown =
        "You are currently on cooldown, wait **%d seconds** to use the command again.";

    if (!e.getChannelType().isGuild()) {
      return;
    }

    Message msg = e.getMessage();

    if (e.getAuthor().isBot()) {
      return;
    }

    if (isCommand(msg, e)) {
      if (!hasPermission(e)) {
        e.getTextChannel()
            .sendMessage(
                Embeds.create(
                    EmbedTemplate.ERROR,
                    e.getAuthor(),
                    String.format(invalidPermission, getPermissionString()),
                    e.getGuild(),
                    null))
            .queue();
        return;
      }

      if ((getCategory() == Category.ADMIN)
          && !e.getAuthor().getId().equals(Corby.config.getOwnerId())) {
        return;
      }

      if (e.getGuild().getBotRole() == null
          || !Objects.requireNonNull(e.getGuild().getBotRole()).hasPermission(Corby.permissions)) {
        e.getTextChannel()
            .sendMessage(
                Embeds.create(
                    EmbedTemplate.ERROR,
                    e.getAuthor(),
                    String.format(invalidBotPermission, Corby.config.getInviteUrl()),
                    e.getGuild(),
                    null))
            .queue();
        e.getGuild().leave().queue();
        return;
      }

      if (!isValidSyntax(e, getCommandArgs(e.getMessage()))) {
        StringBuilder sb = new StringBuilder();
        for (String s : getUsages()) {
          sb.append("`")
              .append(String.format(s, PrefixManager.getPrefix(e.getGuild())))
              .append("`")
              .append("\n");
        }

        e.getTextChannel()
            .sendMessage(
                Embeds.create(
                    EmbedTemplate.ERROR,
                    e.getAuthor(),
                    String.format(invalidSyntax, e.getMessage().getContentRaw(), sb),
                    e.getGuild(),
                    null))
            .queue();
        return;
      }

      int cooldownTime = CooldownsManager.getCooldown(e.getAuthor(), this);

      if (cooldownTime > 0) {
        e.getTextChannel()
            .sendMessage(
                Embeds.create(
                    EmbedTemplate.ERROR,
                    e.getAuthor(),
                    String.format(cooldown, cooldownTime),
                    e.getGuild(),
                    null))
            .queue();
        return;
      }

      try {
        execute(e, getCommandArgs(msg));
      } catch (Exception exception) {
        ExceptionUtils.processException(exception);
      }

      CooldownsManager.setCooldown(new Cooldown(e.getAuthor(), this.getCooldown(), this));
    }
  }

  protected String getMessageContent() {
    return event.getMessage().getContentRaw();
  }

  private boolean hasPermission(MessageReceivedEvent event) {
    if (getPermissions().length == 0) {
      return true;
    }

    return Objects.requireNonNull(event.getMember())
            .getPermissions()
            .containsAll(Arrays.asList(getPermissions()))
        || event
            .getAuthor()
            .getId()
            .equals(
                Corby.config.getOwnerId()); // <- Don't worry, this is only needed to test the bot.
  }

  private String getPermissionString() {

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < getPermissions().length; i++) {
      sb.append(getPermissions()[i].getName())
          .append((i == getPermissions().length - 1) ? "" : ", ");
    }

    return sb.toString();
  }

  private String[] getCommandArgs(Message msg) {
    return msg.getContentRaw().split("\\s+");
  }

  private boolean isCommand(Message message, MessageReceivedEvent event) {

    if (!getCommands().contains(this)) {
      return false;
    }

    return Arrays.asList(getCommandArgs(message))
            .get(0)
            .toLowerCase()
            .equals(PrefixManager.getPrefix(event.getGuild()) + getAlias())
        && getCommandArgs(message)[0].startsWith(PrefixManager.getPrefix(event.getGuild()));
  }
}
