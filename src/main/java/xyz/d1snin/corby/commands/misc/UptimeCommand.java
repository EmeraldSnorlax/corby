/*                          GNU GENERAL PUBLIC LICENSE
 *                            Version 3, 29 June 2007
 *
 *        Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 *            Everyone is permitted to copy and distribute verbatim copies
 *             of this license document, but changing it is not allowed.
 */

package xyz.d1snin.corby.commands.misc;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import xyz.d1snin.corby.Corby;
import xyz.d1snin.corby.commands.Command;
import xyz.d1snin.corby.enums.Category;
import xyz.d1snin.corby.enums.EmbedTemplate;
import xyz.d1snin.corby.utils.Embeds;
import xyz.d1snin.corby.utils.OtherUtils;

public class UptimeCommand extends Command {

  public UptimeCommand() {
    this.alias = "uptime";
    this.description = "Get the bot's uptime";
    this.category = Category.MISC;
  }

  @Override
  protected void execute(MessageReceivedEvent e, String[] args) {
    e.getTextChannel()
        .sendMessage(
            Embeds.create(
                EmbedTemplate.DEFAULT,
                e.getAuthor(),
                OtherUtils.formatMessageKeyText(
                    "Uptime", String.format("[ %s ]", Corby.getUptime())),
                e.getGuild()))
        .queue();
  }

  @Override
  protected boolean isValidSyntax(MessageReceivedEvent e, String[] args) {
    return args.length < 2;
  }
}
