package io.github.starwishsama.comet.pushers

import io.github.starwishsama.bilibiliapi.LiveApi
import io.github.starwishsama.bilibiliapi.MainApi
import io.github.starwishsama.bilibiliapi.data.live.LiveRoomInfo
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.BotVariables.bot
import io.github.starwishsama.comet.BotVariables.cfg
import io.github.starwishsama.comet.BotVariables.daemonLogger
import io.github.starwishsama.comet.commands.CommandExecutor.doFilter
import io.github.starwishsama.comet.utils.StringUtil.convertToChain
import io.github.starwishsama.comet.utils.verboseS
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.getGroupOrNull
import net.mamoe.mirai.message.data.isContentNotEmpty
import java.util.concurrent.ScheduledFuture

object BiliLiveChecker : CometPusher {
    private val pushedList = mutableListOf<StoredLiveInfo>()
    override val delayTime: Long = cfg.biliInterval
    override val internal: Long = cfg.biliInterval
    override var future: ScheduledFuture<*>? = null

    override fun retrieve() {
        if (!bot.isOnline) future?.cancel(false)

        var count = 0

        val collectedUsers = mutableSetOf<Long>()

        BotVariables.perGroup.parallelStream().forEach {
            if (it.biliPushEnabled) {
                collectedUsers.plusAssign(it.biliSubscribers)
            }
        }

        collectedUsers.parallelStream().forEach { roomId ->
            val data = LiveApi.getLiveInfo(roomId)

            if (data != null) {
                val sli = StoredLiveInfo(data.data, false)
                if (pushedList.isEmpty() && data.data.liveStatus == 1) {
                    pushedList.plusAssign(sli)
                    count++
                } else {
                    var hasOldData = false

                    for (i in pushedList.indices) {
                        val oldStatus = pushedList[i].data.liveStatus
                        val currentStatus = data.data.liveStatus
                        if (pushedList[i].data.roomId == roomId) {
                            hasOldData = true
                            if (oldStatus != currentStatus && currentStatus == 1) {
                                pushedList[i] = sli
                            }
                            break
                        }
                    }

                    if (!hasOldData && data.data.liveStatus == 1) {
                        pushedList.add(sli)
                        count++
                    }
                }
            }
        }

        if (count > 0) daemonLogger.verboseS("Retrieve success, have collected $count liver(s)!")

        push()
    }

    override fun push() {
        val liverToGroups = mutableMapOf<StoredLiveInfo, MutableSet<Long>>()
        pushedList.parallelStream().forEach { liverToGroups.plusAssign(it to mutableSetOf()) }

        BotVariables.perGroup.parallelStream().forEach { cfg ->
            if (cfg.biliPushEnabled) {
                liverToGroups.forEach {
                    if (cfg.biliSubscribers.contains(it.key.getRoomId())) {
                        it.value.plusAssign(cfg.id)
                    }
                }
            }
        }

        val count = pushToGroups(liverToGroups)
        if (count > 0) daemonLogger.verboseS("Push bili info success, have pushed $count group(s)!")
    }

    private fun pushToGroups(pushQueue: MutableMap<StoredLiveInfo, MutableSet<Long>>): Int {
        var count = 0

        /** 遍历推送列表推送开播消息 */
        pushQueue.forEach { (info, pushGroups) ->
            if (!info.isPushed) {
                val data = info.data
                if (data.liveStatus != 0) {
                    val msg = "单推助手 > \n${MainApi.getUserNameByMid(data.uid)} 正在直播!" +
                            "\n直播间标题: ${data.title}" +
                            "\n开播时间: ${data.liveTime}" +
                            "\n传送门: https://live.bilibili.com/${data.roomId}"
                    pushGroups.forEach {
                        val filtered = msg.convertToChain().doFilter()
                        if (filtered.isContentNotEmpty()) {
                            runBlocking {
                                bot.getGroupOrNull(it)?.sendMessage(filtered)
                                count++
                                delay(2_500)
                            }
                        }
                    }
                }
                info.isPushed = true
            }
        }

        return count
    }

    data class StoredLiveInfo(val data: LiveRoomInfo.LiveRoomInfoData, var isPushed: Boolean) {
        fun getRoomId() = data.roomId
    }
}