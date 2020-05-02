package io.github.starwishsama.nbot.listeners

import io.github.starwishsama.nbot.BotConstants
import io.github.starwishsama.nbot.BotInstance
import io.github.starwishsama.nbot.commands.interfaces.WaitableCommand
import io.github.starwishsama.nbot.enums.SessionType
import io.github.starwishsama.nbot.objects.BotUser
import io.github.starwishsama.nbot.sessions.SessionManager
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.subscribeGroupMessages

object SessionListener : NListener {
    override fun register(bot: Bot) {
        bot.subscribeGroupMessages {
            always {
                if (!isPrefix(message.contentToString())) {
                    if (SessionManager.isValidSession(sender.id)) {
                        val session = SessionManager.getSession(sender.id)
                        if (session != null) {
                            val command = session.command
                            if (session.type == SessionType.DELAY && command is WaitableCommand) {
                                SessionManager.expireSession(sender.id)
                                var user = BotUser.getUser(sender.id)
                                if (user == null) {
                                    user = BotUser.quickRegister(sender.id)
                                }
                                BotInstance.logger.info("Executing waitable command")
                                command.replyResult(this, user, session)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getName(): String = "会话"

    private fun isPrefix(message: String): Boolean {
        if (message.isNotEmpty()) {
            for (prefix in BotConstants.cfg.commandPrefix) {
                if (message.startsWith(prefix)) {
                    return true
                }
            }
        }
        return false
    }
}