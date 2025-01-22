package android.llama.cpp

import androidx.annotation.Keep

@Keep
data class LLamaMessage(val role:Role, val content:String)
fun LLamaMessage.isUser()=this.role==Role.User


sealed interface EventState{
    data object Idle:EventState
    data object Loaded:EventState
    data object Busy:EventState
}



@Keep
enum class Role{
    System,
    User,
    Assistant
}

