package android.llama.cpp

import androidx.annotation.Keep

@Keep
data class Message(val role:Role,val content:String)
fun Message.isUser()=this.role==Role.User

@Keep
enum class Role{
    System,
    User,
    Assistant
}

