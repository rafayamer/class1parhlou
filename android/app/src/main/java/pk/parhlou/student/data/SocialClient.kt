package pk.parhlou.student.data

import org.json.JSONObject
import pk.parhlou.student.domain.Social

/** Leagues, friends and class groups through the signed-in student's own session. Null/empty when offline or signed out. */
open class SocialClient(private val sync: SyncClient?) {
    open suspend fun me(): Social.Me? = sync?.rpc("social_me")?.let(Social::me)
    open suspend fun setAvatar(avatar: String): Boolean = sync?.rpc("social_set_avatar", JSONObject().put("p_avatar", avatar)) != null
    /** Posts this week's XP and streak, and returns the league board. */
    open suspend fun postWeek(xp: Int, streak: Int): Social.Board? =
        sync?.rpc("social_post_week", JSONObject().put("p_xp", xp).put("p_streak", streak))?.let(Social::board)
    open suspend fun friends(): List<Social.Friend> = sync?.rpc("friends_list")?.let(Social::friends).orEmpty()
    /** Adds a friend by code: the new list, or an error code. */
    open suspend fun addFriend(code: String): Pair<List<Social.Friend>?, String?> {
        val (text, err) = sync?.rpcOrError("friend_add", JSONObject().put("p_code", code)) ?: return null to "OFFLINE"
        Social.errorOf(text)?.let { return null to it }
        return text?.let(Social::friends) to err
    }
    open suspend fun removeFriend(code: String): Boolean = sync?.rpc("friend_remove", JSONObject().put("p_code", code)) != null
    open suspend fun nudge(code: String): Boolean = sync?.rpc("nudge", JSONObject().put("p_code", code)) != null
    open suspend fun nudges(): List<Social.Nudge> = sync?.rpc("my_nudges")?.let(Social::nudges).orEmpty()
    open suspend fun groups(): List<Social.Group> = sync?.rpc("my_groups")?.let(Social::groups).orEmpty()
    open suspend fun createGroup(name: String): String? = sync?.rpcOrError("group_create", JSONObject().put("p_name", name)).let { it?.second ?: if (it == null) "OFFLINE" else null }
    open suspend fun joinGroup(code: String): String? = sync?.rpcOrError("group_join", JSONObject().put("p_code", code))
        .let { it?.second ?: Social.errorOf(it?.first) ?: if (it == null) "OFFLINE" else null }
    open suspend fun leaveGroup(code: String): Boolean = sync?.rpc("group_leave", JSONObject().put("p_code", code)) != null
}
